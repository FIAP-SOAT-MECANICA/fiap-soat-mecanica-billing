package br.com.fiap.soat.mecanica.billing.adapters.in.scheduling;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.adapters.out.messaging.LocalStackSupport;
import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** The job runs on its own: an overdue budget becomes EXPIRADO and the OS hears OrcamentoRecusado through SNS. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ExpiracaoOrcamentoTest {
    static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("test");
    static final LocalStackSupport.Canal eventos;
    static final List<JsonNode> recebidos = new ArrayList<>();

    static {
        db.start();
        eventos = LocalStackSupport.novoCanal("expiracao-eventos");
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
        registry.add("billing.expiracao.habilitada", () -> "true");
        registry.add("billing.expiracao.intervalo-ms", () -> "200");
        registry.add("billing.eventos.topic-arn", eventos::topicArn);
        registry.add("billing.outbox.intervalo-ms", () -> "200");
        registry.add("billing.aws.endpoint", () -> LocalStackSupport.localstack.getEndpoint().toString());
        registry.add("billing.aws.region", LocalStackSupport.localstack::getRegion);
        registry.add("billing.aws.access-key", () -> "test");
        registry.add("billing.aws.secret-key", () -> "test");
    }

    @Autowired OrcamentoRepository orcamentos;
    @Autowired ObjectMapper mapper;

    Orcamento gravar(Instant criado, Instant expira, boolean aprovar) {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Servico", new BigDecimal("50.00"), List.of());
        var o = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(item), criado, expira);
        return orcamentos.inserir(aprovar ? o.aprovar(criado.plusSeconds(1)) : o).orcamento();
    }

    EstadoOrcamento estado(Orcamento o) { return orcamentos.buscarPorId(o.id()).orElseThrow().orcamento().estado(); }

    List<JsonNode> eventosDe(Orcamento o) {
        LocalStackSupport.receber(eventos.queueUrl(), 1).forEach(m -> {
            try { recebidos.add(mapper.readTree(m.body())); } catch (Exception e) { throw new IllegalStateException(e); }
        });
        return recebidos.stream().filter(e -> e.path("osId").asText().equals(o.osId().toString())).toList();
    }

    @Test void vencidoExpiraEOOsRecebeOrcamentoRecusadoEnquantoOsDemaisFicamComoEstao() throws Exception {
        Instant agora = Instant.now();
        var vencido = gravar(agora.minusSeconds(7200), agora.minusSeconds(60), false);
        var vigente = gravar(agora.minusSeconds(60), agora.plusSeconds(3600), false);
        var aprovadoVencido = gravar(agora.minusSeconds(7200), agora.minusSeconds(60), true);

        await().atMost(Duration.ofSeconds(30)).until(() -> estado(vencido) == EstadoOrcamento.EXPIRADO);
        await().atMost(Duration.ofSeconds(30)).until(() -> !eventosDe(vencido).isEmpty());

        JsonNode evento = eventosDe(vencido).get(0);
        assertEquals("OrcamentoRecusado", evento.path("type").asText());
        assertEquals(1, evento.path("version").asInt());
        assertEquals(vencido.osId().toString(), evento.path("payload").path("osId").asText());
        assertEquals(vencido.id().toString(), evento.path("payload").path("orcamentoId").asText());
        assertEquals("EXPIRADO", evento.path("payload").path("motivo").asText());

        // Runs again and again without announcing twice; budgets not overdue, or already decided, are untouched.
        Thread.sleep(1500);
        assertEquals(1, eventosDe(vencido).size());
        assertEquals(EstadoOrcamento.GERADO, estado(vigente));
        assertEquals(EstadoOrcamento.APROVADO, estado(aprovadoVencido));
        assertTrue(eventosDe(vigente).isEmpty());
        assertTrue(eventosDe(aprovadoVencido).isEmpty());
    }
}
