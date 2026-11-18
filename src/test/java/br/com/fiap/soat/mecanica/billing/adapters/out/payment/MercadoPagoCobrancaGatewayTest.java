package br.com.fiap.soat.mecanica.billing.adapters.out.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.FalhaCobrancaException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MercadoPagoCobrancaGatewayTest {
    static final String BASE = "https://mp.test";
    UUID osId = UUID.randomUUID(), orcamentoId = UUID.randomUUID();
    String referencia = UUID.randomUUID().toString();

    MockRestServiceServer servidor;

    CobrancaGateway gateway(String notificationUrl) {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        return new MercadoPagoCobrancaGateway(builder, BASE, "TOKEN-SECRETO", notificationUrl);
    }

    void buscaRetorna(String json) {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("external_reference", referencia))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TOKEN-SECRETO"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test void criaPreferenciaComReferenciaValorEUrlDeNotificacao() {
        var gateway = gateway("https://billing.test/webhooks/mercadopago");
        servidor.expect(requestTo(BASE + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TOKEN-SECRETO"))
                .andExpect(header("X-Idempotency-Key", referencia))
                .andExpect(jsonPath("$.external_reference").value(referencia))
                .andExpect(jsonPath("$.notification_url").value("https://billing.test/webhooks/mercadopago"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].unit_price").value(190.5))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.items[0].currency_id").value("BRL"))
                .andExpect(jsonPath("$.items[0].title").value("Servicos da OS " + osId))
                .andExpect(jsonPath("$.metadata.os_id").value(osId.toString()))
                .andExpect(jsonPath("$.metadata.orcamento_id").value(orcamentoId.toString()))
                .andRespond(withSuccess("{\"id\":\"123-abc\",\"init_point\":\"https://mp.test/checkout?pref=123-abc\"}",
                        MediaType.APPLICATION_JSON));

        var cobranca = gateway.criar(referencia, osId, orcamentoId, new BigDecimal("190.50"));

        assertEquals(referencia, cobranca.referenciaExterna());
        assertEquals("https://mp.test/checkout?pref=123-abc", cobranca.linkPagamento());
        servidor.verify();
    }

    @Test void semUrlDeNotificacaoOCampoNaoEhEnviado() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/checkout/preferences"))
                .andExpect(jsonPath("$.notification_url").doesNotExist())
                .andRespond(withSuccess("{\"init_point\":\"https://mp.test/c\"}", MediaType.APPLICATION_JSON));

        gateway.criar(referencia, osId, orcamentoId, BigDecimal.TEN);
        servidor.verify();
    }

    @Test void erroDoProvedorViraFalhaDeCobrancaSemVazarOToken() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/checkout/preferences")).andRespond(withServerError());

        var erro = assertThrows(FalhaCobrancaException.class,
                () -> gateway.criar(referencia, osId, orcamentoId, BigDecimal.TEN));
        assertFalse(erro.getMessage().contains("TOKEN-SECRETO"));
    }

    @Test void credencialRecusadaViraFalhaDeCobranca() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/checkout/preferences")).andRespond(withUnauthorizedRequest());
        assertThrows(FalhaCobrancaException.class,
                () -> gateway.criar(referencia, osId, orcamentoId, BigDecimal.TEN));
    }

    @Test void respostaSemLinkDeCheckoutEhFalha() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/checkout/preferences"))
                .andRespond(withSuccess("{\"id\":\"1\"}", MediaType.APPLICATION_JSON));
        assertThrows(FalhaCobrancaException.class,
                () -> gateway.criar(referencia, osId, orcamentoId, BigDecimal.TEN));
    }

    @Test void respostaVaziaEhFalha() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/checkout/preferences")).andRespond(withSuccess());
        assertThrows(FalhaCobrancaException.class,
                () -> gateway.criar(referencia, osId, orcamentoId, BigDecimal.TEN));
    }

    @Test void consultaComPagamentoAprovadoEhAprovadaMesmoComTentativaRecusada() {
        var gateway = gateway("");
        buscaRetorna("{\"results\":[{\"status\":\"approved\"},{\"status\":\"rejected\"}]}");
        assertEquals(EstadoPagamento.APROVADO, gateway.consultar(referencia));
    }

    @Test void consultaComTodasAsTentativasRecusadasOuCanceladasEhRecusada() {
        var gateway = gateway("");
        buscaRetorna("{\"results\":[{\"status\":\"rejected\"},{\"status\":\"cancelled\"}]}");
        assertEquals(EstadoPagamento.RECUSADO, gateway.consultar(referencia));
    }

    @Test void consultaComTentativaEmAndamentoContinuaPendente() {
        var gateway = gateway("");
        buscaRetorna("{\"results\":[{\"status\":\"rejected\"},{\"status\":\"in_process\"}]}");
        assertEquals(EstadoPagamento.PENDENTE, gateway.consultar(referencia));
    }

    @Test void consultaSemPagamentosAindaEhPendente() {
        var gateway = gateway("");
        buscaRetorna("{\"results\":[]}");
        assertEquals(EstadoPagamento.PENDENTE, gateway.consultar(referencia));
    }

    @Test void consultaSemCorpoEhPendente() {
        var gateway = gateway("");
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andRespond(withSuccess());
        assertEquals(EstadoPagamento.PENDENTE, gateway.consultar(referencia));
    }

    @Test void consultaComErroDoProvedorEhFalha() {
        var gateway = gateway("");
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY));
        assertThrows(FalhaCobrancaException.class, () -> gateway.consultar(referencia));
    }

    void pagamentoRetorna(String json) {
        servidor.expect(requestTo(BASE + "/v1/payments/9001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TOKEN-SECRETO"))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }

    @Test void pagamentoAprovadoTrazAReferenciaNossa() {
        var gateway = gateway("");
        pagamentoRetorna("{\"id\":9001,\"status\":\"approved\",\"external_reference\":\"" + referencia + "\"}");
        var p = gateway.consultarPagamento("9001").orElseThrow();
        assertEquals(referencia, p.referenciaExterna());
        assertEquals(EstadoPagamento.APROVADO, p.estado());
    }

    @Test void statusDoPagamentoViraEstadoNosso() {
        var gateway = gateway("");
        for (String[] caso : new String[][] {{"rejected", "RECUSADO"}, {"cancelled", "RECUSADO"}, {"pending", "PENDENTE"},
                {"in_process", "PENDENTE"}, {"authorized", "PENDENTE"}, {"refunded", "PENDENTE"}}) {
            servidor.reset();
            pagamentoRetorna("{\"status\":\"" + caso[0] + "\",\"external_reference\":\"x\"}");
            assertEquals(EstadoPagamento.valueOf(caso[1]), gateway.consultarPagamento("9001").orElseThrow().estado(), caso[0]);
        }
    }

    @Test void pagamentoSemReferenciaNossaVemComReferenciaNula() {
        var gateway = gateway("");
        pagamentoRetorna("{\"status\":\"approved\"}");
        assertNull(gateway.consultarPagamento("9001").orElseThrow().referenciaExterna());
    }

    @Test void pagamentoDesconhecidoDoProvedorEhVazio() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/v1/payments/9001")).andRespond(withResourceNotFound());
        assertTrue(gateway.consultarPagamento("9001").isEmpty());
    }

    @Test void pagamentoComRespostaVaziaEhVazio() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/v1/payments/9001")).andRespond(withSuccess());
        assertTrue(gateway.consultarPagamento("9001").isEmpty());
    }

    @Test void erroDoProvedorAoConsultarPagamentoEhFalha() {
        var gateway = gateway("");
        servidor.expect(requestTo(BASE + "/v1/payments/9001")).andRespond(withServerError());
        assertThrows(FalhaCobrancaException.class, () -> gateway.consultarPagamento("9001"));
        servidor.reset();
        servidor.expect(requestTo(BASE + "/v1/payments/9001")).andRespond(withUnauthorizedRequest());
        assertThrows(FalhaCobrancaException.class, () -> gateway.consultarPagamento("9001"));
    }
}
