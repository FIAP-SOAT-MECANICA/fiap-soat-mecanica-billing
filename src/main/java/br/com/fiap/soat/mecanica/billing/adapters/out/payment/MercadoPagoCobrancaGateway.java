package br.com.fiap.soat.mecanica.billing.adapters.out.payment;

import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.FalhaCobrancaException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Checkout Pro: a preference carries the budget total and our reference, the customer pays on the provider's page,
 * and the provider reports each payment with that reference. Never logs the access token.
 */
public class MercadoPagoCobrancaGateway implements CobrancaGateway {
    private final RestClient http;
    private final String notificationUrl;

    /** @param notificationUrl public URL of the webhook; blank leaves notifications to the provider's account setup */
    public MercadoPagoCobrancaGateway(RestClient.Builder builder, String baseUrl, String accessToken,
                                      String notificationUrl) {
        this.http = builder.baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .build();
        this.notificationUrl = notificationUrl;
    }

    @Override
    public Cobranca criar(String referenciaExterna, UUID osId, UUID orcamentoId, BigDecimal valor) {
        var item = Map.of("id", orcamentoId.toString(), "title", "Servicos da OS " + osId, "quantity", 1,
                "unit_price", valor, "currency_id", "BRL");
        var corpo = new java.util.LinkedHashMap<String, Object>();
        corpo.put("items", List.of(item));
        corpo.put("external_reference", referenciaExterna);
        corpo.put("metadata", Map.of("os_id", osId.toString(), "orcamento_id", orcamentoId.toString()));
        if (notificationUrl != null && !notificationUrl.isBlank()) corpo.put("notification_url", notificationUrl);

        JsonNode resposta = chamar(() -> http.post().uri("/checkout/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                // The same reference always maps to the same checkout request.
                .header("X-Idempotency-Key", referenciaExterna)
                .body(corpo).retrieve().body(JsonNode.class), "criar cobranca");
        String link = resposta == null ? null : resposta.path("init_point").asText(null);
        if (link == null || link.isBlank()) throw new FalhaCobrancaException("resposta do provedor sem init_point");
        return new Cobranca(referenciaExterna, link);
    }

    @Override
    public EstadoPagamento consultar(String referenciaExterna) {
        JsonNode resposta = chamar(() -> http.get().uri(uri -> uri.path("/v1/payments/search")
                        .queryParam("external_reference", referenciaExterna)
                        .queryParam("sort", "date_created").queryParam("criteria", "desc").build())
                .retrieve().body(JsonNode.class), "consultar cobranca");
        JsonNode resultados = resposta == null ? null : resposta.path("results");
        if (resultados == null || !resultados.isArray() || resultados.isEmpty()) return EstadoPagamento.PENDENTE;

        boolean todosFalharam = true;
        for (JsonNode pagamento : resultados) {
            String status = pagamento.path("status").asText();
            if (status.equals("approved")) return EstadoPagamento.APROVADO;
            if (!status.equals("rejected") && !status.equals("cancelled")) todosFalharam = false;
        }
        return todosFalharam ? EstadoPagamento.RECUSADO : EstadoPagamento.PENDENTE;
    }

    @Override
    public Optional<PagamentoProvedor> consultarPagamento(String paymentId) {
        JsonNode pagamento;
        try {
            pagamento = chamar(() -> http.get().uri("/v1/payments/{id}", paymentId).retrieve().body(JsonNode.class),
                    "consultar pagamento");
        } catch (FalhaCobrancaException e) {
            if (e.getCause() instanceof HttpClientErrorException.NotFound) return Optional.empty();
            throw e;
        }
        if (pagamento == null || pagamento.isMissingNode()) return Optional.empty();
        return Optional.of(new PagamentoProvedor(pagamento.path("external_reference").asText(null),
                estadoDe(pagamento.path("status").asText())));
    }

    /** approved settles it; rejected and cancelled refuse it; everything else (pending, in_process, refunds) waits. */
    private static EstadoPagamento estadoDe(String status) {
        return switch (status) {
            case "approved" -> EstadoPagamento.APROVADO;
            case "rejected", "cancelled" -> EstadoPagamento.RECUSADO;
            default -> EstadoPagamento.PENDENTE;
        };
    }

    private static <T> T chamar(java.util.function.Supplier<T> chamada, String acao) {
        try {
            return chamada.get();
        } catch (RestClientException e) {
            // The message of a response exception carries status and body, never our headers.
            throw new FalhaCobrancaException("falha ao " + acao + " no Mercado Pago", e);
        }
    }
}
