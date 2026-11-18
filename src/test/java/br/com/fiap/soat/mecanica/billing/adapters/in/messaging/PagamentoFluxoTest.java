package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.soat.mecanica.billing.adapters.out.messaging.LocalStackSupport;
import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway;
import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway.PagamentoProvedor;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The payment half of the Saga with a real PostgreSQL and a LocalStack broker: SolicitarPagamento opens the
 * charge, a signed webhook decides it and PagamentoConfirmado leaves through the outbox. Only the provider is faked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PagamentoFluxoTest {
    static final String SEGREDO = "segredo-do-webhook";
    static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("test");
    static final LocalStackSupport.Canal eventos;
    static final String filaComandos;
    static final List<JsonNode> recebidos = new ArrayList<>();

    static {
        db.start();
        eventos = LocalStackSupport.novoCanal("pagamento-eventos");
        filaComandos = LocalStackSupport.novaFila("pagamento-billing");
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
        registry.add("billing.mercadopago.access-token", () -> "TOKEN");
        registry.add("billing.mercadopago.webhook-secret", () -> SEGREDO);
        registry.add("billing.aws.endpoint", () -> LocalStackSupport.localstack.getEndpoint().toString());
        registry.add("billing.aws.region", LocalStackSupport.localstack::getRegion);
        registry.add("billing.aws.access-key", () -> "test");
        registry.add("billing.aws.secret-key", () -> "test");
    }

    @MockitoBean CobrancaGateway gateway;
    @Autowired MockMvc mvc;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;

    UUID osId = UUID.randomUUID(), orcamentoId;

    void orcamentoAprovado() {
        Instant agora = Instant.now();
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Troca de oleo", new BigDecimal("190.50"), List.of());
        var o = Orcamento.gerar(UUID.randomUUID(), osId, UUID.randomUUID(), List.of(item), agora, agora.plusSeconds(3600))
                .aprovar(agora);
        orcamentoId = orcamentos.inserir(o).orcamento().id();
        when(gateway.criar(anyString(), any(), any(), any()))
                .thenAnswer(i -> new CobrancaGateway.Cobranca(i.getArgument(0), "https://pagar/e2e"));
    }

    void enviar(String tipo) {
        String corpo = """
                {"messageId":"%s","type":"%s","version":1,"sagaId":"%s","osId":"%s","occurredAt":"2026-11-17T12:00:00Z",
                 "payload":{"osId":"%s","orcamentoId":"%s"}}""".formatted(UUID.randomUUID(), tipo, UUID.randomUUID(),
                osId, osId, orcamentoId);
        LocalStackSupport.sqs.sendMessage(b -> b.queueUrl(filaComandos).messageBody(corpo));
    }

    String cobranca(String coluna) {
        return jdbc.sql("SELECT " + coluna + "::text FROM pagamentos WHERE os_id = :os ORDER BY criado_em DESC LIMIT 1")
                .param("os", osId).query(String.class).optional().orElse(null);
    }

    String solicitarECarregar() {
        orcamentoAprovado();
        enviar("SolicitarPagamento");
        await().atMost(Duration.ofSeconds(30)).until(() -> cobranca("estado") != null);
        assertEquals("PENDENTE", cobranca("estado"));
        assertEquals("https://pagar/e2e", cobranca("link_pagamento"));
        assertEquals("190.50", cobranca("valor"));
        assertEquals(cobranca("id"), cobranca("referencia_externa"));
        return cobranca("referencia_externa");
    }

    static String assinatura(String dataId, String requestId, String ts) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifesto = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    int webhook(String dataId, String eventoId, String tipo, String assinaturaHeader) throws Exception {
        var req = post("/webhooks/mercadopago").param("data.id", dataId).param("type", tipo)
                .header("x-request-id", "req-" + eventoId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":" + eventoId + ",\"type\":\"" + tipo + "\",\"action\":\"payment.updated\","
                        + "\"data\":{\"id\":\"" + dataId + "\"}}");
        if (assinaturaHeader != null) req.header("x-signature", assinaturaHeader);
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    List<JsonNode> eventosDaOs(String tipo) {
        LocalStackSupport.receber(eventos.queueUrl(), 1).forEach(m -> {
            try { recebidos.add(mapper.readTree(m.body())); } catch (Exception e) { throw new IllegalStateException(e); }
        });
        return recebidos.stream().filter(e -> e.path("osId").asText().equals(osId.toString())
                && e.path("type").asText().equals(tipo)).toList();
    }

    @Test void solicitarPagamentoEWebhookAssinadoConfirmamEPublicamOEvento() throws Exception {
        String referencia = solicitarECarregar();
        when(gateway.consultarPagamento("777"))
                .thenReturn(Optional.of(new PagamentoProvedor(referencia, EstadoPagamento.APROVADO)));

        assertEquals(200, webhook("777", "555", "payment", assinatura("777", "req-555", "1700000000")));

        assertEquals("APROVADO", cobranca("estado"));
        await().atMost(Duration.ofSeconds(30)).until(() -> !eventosDaOs("PagamentoConfirmado").isEmpty());
        JsonNode evento = eventosDaOs("PagamentoConfirmado").get(0);
        assertEquals(1, evento.path("version").asInt());
        assertEquals("req-555", evento.path("correlationId").asText());
        assertEquals(osId.toString(), evento.path("payload").path("osId").asText());
        assertEquals(orcamentoId.toString(), evento.path("payload").path("orcamentoId").asText());
        assertEquals(cobranca("id"), evento.path("payload").path("pagamentoId").asText());

        // The provider retries the same notification: accepted again, no second event.
        assertEquals(200, webhook("777", "555", "payment", assinatura("777", "req-555", "1700000000")));
        Thread.sleep(1500);
        assertEquals(1, eventosDaOs("PagamentoConfirmado").size());
    }

    @Test void recusaDoProvedorPublicaPagamentoRecusado() throws Exception {
        String referencia = solicitarECarregar();
        when(gateway.consultarPagamento("778"))
                .thenReturn(Optional.of(new PagamentoProvedor(referencia, EstadoPagamento.RECUSADO)));

        assertEquals(200, webhook("778", "556", "payment", assinatura("778", "req-556", "1700000001")));

        assertEquals("RECUSADO", cobranca("estado"));
        await().atMost(Duration.ofSeconds(30)).until(() -> !eventosDaOs("PagamentoRecusado").isEmpty());
        assertTrue(eventosDaOs("PagamentoConfirmado").isEmpty());
    }

    @Test void webhookSemAssinaturaValidaEhRecusadoESemEfeito() throws Exception {
        String referencia = solicitarECarregar();
        when(gateway.consultarPagamento("779"))
                .thenReturn(Optional.of(new PagamentoProvedor(referencia, EstadoPagamento.APROVADO)));

        assertEquals(401, webhook("779", "557", "payment", null));
        assertEquals(401, webhook("779", "557", "payment", "ts=1,v1=00"));
        assertEquals(401, webhook("780", "557", "payment", assinatura("779", "req-557", "1"))); // signed for another id

        verify(gateway, never()).consultarPagamento(any());
        assertEquals("PENDENTE", cobranca("estado"));
    }

    @Test void notificacaoDeOutroTipoEhAceitaSemConsultarOProvedor() throws Exception {
        assertEquals(200, webhook("900", "558", "merchant_order", assinatura("900", "req-558", "1700000002")));
        verify(gateway, never()).consultarPagamento(any());
    }

    @Test void provedorForaDoArFazOWebhookFalharParaELeTentarDeNovo() throws Exception {
        String referencia = solicitarECarregar();
        when(gateway.consultarPagamento("781")).thenThrow(new br.com.fiap.soat.mecanica.billing.domain.FalhaCobrancaException("fora"))
                .thenReturn(Optional.of(new PagamentoProvedor(referencia, EstadoPagamento.APROVADO)));

        assertEquals(502, webhook("781", "559", "payment", assinatura("781", "req-559", "1700000003")));
        assertEquals("PENDENTE", cobranca("estado"));
        // Same notification again: the failed attempt left nothing behind, so it is processed now.
        assertEquals(200, webhook("781", "559", "payment", assinatura("781", "req-559", "1700000003")));
        assertEquals("APROVADO", cobranca("estado"));
    }

    @Test void cancelarCobrancaRetiraACobrancaPendenteEUmaAprovacaoTardiaNaoAMuda() throws Exception {
        String referencia = solicitarECarregar();
        enviar("CancelarCobranca");
        await().atMost(Duration.ofSeconds(30)).until(() -> "CANCELADO".equals(cobranca("estado")));

        when(gateway.consultarPagamento("782"))
                .thenReturn(Optional.of(new PagamentoProvedor(referencia, EstadoPagamento.APROVADO)));
        assertEquals(200, webhook("782", "560", "payment", assinatura("782", "req-560", "1700000004")));

        assertEquals("CANCELADO", cobranca("estado"));
        Thread.sleep(1500);
        assertTrue(eventosDaOs("PagamentoConfirmado").isEmpty());
    }

    @Test void solicitarPagamentoRepetidoNaoAbreSegundaCobranca() throws Exception {
        solicitarECarregar();
        enviar("SolicitarPagamento"); // new messageId, same budget
        Thread.sleep(3000);
        assertEquals(1, jdbc.sql("SELECT count(*) FROM pagamentos WHERE os_id = :os").param("os", osId)
                .query(Integer.class).single());
        verify(gateway, times(1)).criar(anyString(), eq(osId), eq(orcamentoId), any());
    }
}
