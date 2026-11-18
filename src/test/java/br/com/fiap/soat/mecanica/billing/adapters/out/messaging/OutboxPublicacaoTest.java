package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real PostgreSQL and a LocalStack broker: the scheduled job drains what the API wrote to the outbox. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxPublicacaoTest {
    static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("test");
    static final LocalStackSupport.Canal canal;

    static {
        db.start();
        canal = LocalStackSupport.novoCanal("outbox-job");
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
        registry.add("billing.eventos.topic-arn", canal::topicArn);
        registry.add("billing.aws.endpoint", () -> LocalStackSupport.localstack.getEndpoint().toString());
        registry.add("billing.aws.region", LocalStackSupport.localstack::getRegion);
        registry.add("billing.aws.access-key", () -> "test");
        registry.add("billing.aws.secret-key", () -> "test");
        registry.add("billing.outbox.intervalo-ms", () -> "200");
    }

    @Autowired OutboxRepository outbox;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;

    @Test void agendadorPublicaEMarcaOsEventosGravadosNaOutbox() throws Exception {
        UUID primeiro = UUID.randomUUID();
        UUID segundo = UUID.randomUUID();
        Instant agora = Instant.now();
        outbox.registrar(primeiro, "OrcamentoAprovado", envelope(primeiro, "OrcamentoAprovado"), agora.minusSeconds(2));
        outbox.registrar(segundo, "OrcamentoRecusado", envelope(segundo, "OrcamentoRecusado"), agora.minusSeconds(1));

        List<String> recebidos = new ArrayList<>();
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            LocalStackSupport.receber(canal.queueUrl(), 1).forEach(m -> recebidos.add(m.body()));
            return recebidos.size() >= 2;
        });

        assertEquals(2, recebidos.size());
        assertTrue(recebidos.stream().anyMatch(b -> b.contains(primeiro.toString())));
        assertTrue(recebidos.stream().anyMatch(b -> b.contains(segundo.toString())));
        await().atMost(Duration.ofSeconds(10)).until(() -> pendentes(primeiro, segundo) == 0);

        // Published events are not published again.
        Thread.sleep(1000);
        assertTrue(LocalStackSupport.receber(canal.queueUrl(), 1).isEmpty());
    }

    private int pendentes(UUID... ids) {
        return jdbc.sql("SELECT count(*) FROM outbox WHERE publicado_em IS NULL AND event_id IN (:ids)")
                .param("ids", List.of(ids)).query(Integer.class).single();
    }

    private String envelope(UUID id, String tipo) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("messageId", id.toString(), "type", tipo, "version", 1,
                "osId", UUID.randomUUID().toString(), "payload", java.util.Map.of("orcamentoId", id.toString())));
    }
}
