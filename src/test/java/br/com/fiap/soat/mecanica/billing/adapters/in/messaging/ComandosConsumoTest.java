package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.adapters.out.messaging.LocalStackSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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

/**
 * Real PostgreSQL and a LocalStack broker, with the contract the OS sends: commands arrive on the queue,
 * budgets are stored and the answering events leave through the outbox, SNS and a raw-delivery subscriber.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ComandosConsumoTest {
    static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("test");
    static final LocalStackSupport.Canal eventos;
    static final String filaComandos;
    static final List<JsonNode> recebidos = new ArrayList<>();

    static {
        db.start();
        eventos = LocalStackSupport.novoCanal("comandos-eventos");
        filaComandos = LocalStackSupport.novaFila("comandos-billing");
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
        registry.add("billing.eventos.topic-arn", eventos::topicArn);
        registry.add("billing.comandos.queue-url", () -> filaComandos);
        registry.add("billing.comandos.espera-segundos", () -> "1");
        registry.add("billing.comandos.intervalo-ms", () -> "100");
        registry.add("billing.outbox.intervalo-ms", () -> "200");
        registry.add("billing.aws.endpoint", () -> LocalStackSupport.localstack.getEndpoint().toString());
        registry.add("billing.aws.region", LocalStackSupport.localstack::getRegion);
        registry.add("billing.aws.access-key", () -> "test");
        registry.add("billing.aws.secret-key", () -> "test");
    }

    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;

    String gerar(UUID messageId, UUID sagaId, UUID osId) {
        return """
                {"messageId":"%s","type":"GerarOrcamento","version":1,"sagaId":"%s","osId":"%s","correlationId":"corr-1",
                 "causationId":null,"occurredAt":"2026-11-17T12:00:00Z",
                 "payload":{"osId":"%s","clienteId":"%s","itens":[{"servicoId":"%s","nomeServico":"Troca de oleo",
                 "precoMdo":150.5,"pecas":[{"pecaId":"%s","qtd":2,"valorUnit":20.00}]}]}}"""
                .formatted(messageId, sagaId, osId, osId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    String cancelar(UUID osId, UUID orcamentoId) {
        return """
                {"messageId":"%s","type":"CancelarOrcamento","version":1,"sagaId":"%s","osId":"%s",
                 "occurredAt":"2026-11-17T12:05:00Z","payload":{"osId":"%s","orcamentoId":"%s"}}"""
                .formatted(UUID.randomUUID(), UUID.randomUUID(), osId, osId, orcamentoId);
    }

    void enviar(String corpo) { LocalStackSupport.sqs.sendMessage(b -> b.queueUrl(filaComandos).messageBody(corpo)); }

    int orcamentos(UUID osId) {
        return jdbc.sql("SELECT count(*) FROM orcamentos WHERE os_id = :os").param("os", osId)
                .query(Integer.class).single();
    }

    String estado(UUID osId) {
        return jdbc.sql("SELECT estado FROM orcamentos WHERE os_id = :os ORDER BY revisao DESC LIMIT 1")
                .param("os", osId).query(String.class).single();
    }

    JsonNode eventoDe(UUID osId, String tipo) {
        JsonNode[] achado = new JsonNode[1];
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            LocalStackSupport.receber(eventos.queueUrl(), 1).forEach(m -> {
                try { recebidos.add(mapper.readTree(m.body())); } catch (Exception e) { throw new IllegalStateException(e); }
            });
            synchronized (recebidos) {
                achado[0] = recebidos.stream().filter(e -> e.path("osId").asText().equals(osId.toString())
                        && e.path("type").asText().equals(tipo)).findFirst().orElse(null);
            }
            return achado[0] != null;
        });
        return achado[0];
    }

    @Test void gerarOrcamentoGravaEPublicaOrcamentoGeradoComSagaECausalidade() {
        UUID messageId = UUID.randomUUID(), sagaId = UUID.randomUUID(), osId = UUID.randomUUID();
        enviar(gerar(messageId, sagaId, osId));

        await().atMost(Duration.ofSeconds(30)).until(() -> orcamentos(osId) == 1);
        assertEquals("GERADO", estado(osId));
        assertEquals("190.50", jdbc.sql("SELECT valor_total::text FROM orcamentos WHERE os_id = :os")
                .param("os", osId).query(String.class).single());

        JsonNode evento = eventoDe(osId, "OrcamentoGerado");
        assertEquals(1, evento.path("version").asInt());
        assertEquals(sagaId.toString(), evento.path("sagaId").asText());
        assertEquals(messageId.toString(), evento.path("causationId").asText());
        assertEquals("corr-1", evento.path("correlationId").asText());
        assertEquals(osId.toString(), evento.path("payload").path("osId").asText());
        String orcamentoId = jdbc.sql("SELECT id::text FROM orcamentos WHERE os_id = :os")
                .param("os", osId).query(String.class).single();
        assertEquals(orcamentoId, evento.path("payload").path("orcamentoId").asText());
    }

    @Test void entregaDuplicadaNaoCriaSegundoOrcamentoEComandoRepetidoSoReanuncia() throws Exception {
        UUID messageId = UUID.randomUUID(), osId = UUID.randomUUID();
        String comando = gerar(messageId, UUID.randomUUID(), osId);
        enviar(comando);
        await().atMost(Duration.ofSeconds(30)).until(() -> orcamentos(osId) == 1);
        enviar(comando); // same messageId: redelivery
        enviar(gerar(UUID.randomUUID(), UUID.randomUUID(), osId)); // new messageId, same OS

        // Give the consumer time to take both; nothing new may be created.
        Thread.sleep(3000);
        assertEquals(1, orcamentos(osId));
        // One event for the first command and one for the new command of the same OS (the OS learns the
        // existing id again); the redelivered messageId produced none.
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            eventoDe(osId, "OrcamentoGerado");
            return recebidos.stream().filter(e -> e.path("osId").asText().equals(osId.toString())).count() >= 2;
        });
        Thread.sleep(1500);
        LocalStackSupport.receber(eventos.queueUrl(), 1).forEach(m -> {
            try { recebidos.add(mapper.readTree(m.body())); } catch (Exception e) { throw new IllegalStateException(e); }
        });
        assertEquals(2, recebidos.stream().filter(e -> e.path("osId").asText().equals(osId.toString())).count());
    }

    @Test void cancelarOrcamentoMarcaOOrcamentoComoCancelado() {
        UUID osId = UUID.randomUUID();
        enviar(gerar(UUID.randomUUID(), UUID.randomUUID(), osId));
        await().atMost(Duration.ofSeconds(30)).until(() -> orcamentos(osId) == 1);
        String orcamentoId = jdbc.sql("SELECT id::text FROM orcamentos WHERE os_id = :os")
                .param("os", osId).query(String.class).single();

        enviar(cancelar(osId, UUID.fromString(orcamentoId)));

        await().atMost(Duration.ofSeconds(30)).until(() -> estado(osId).equals("CANCELADO"));
    }

    @Test void mensagemInvalidaNaoImpedeAsSeguintes() {
        UUID osId = UUID.randomUUID();
        enviar("isto nao e json");
        enviar(gerar(UUID.randomUUID(), UUID.randomUUID(), osId));

        await().atMost(Duration.ofSeconds(30)).until(() -> orcamentos(osId) == 1);
    }
}
