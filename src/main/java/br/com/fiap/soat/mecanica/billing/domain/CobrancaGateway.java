package br.com.fiap.soat.mecanica.billing.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** Boundary with the payment provider (Mercado Pago). */
public interface CobrancaGateway {
    /**
     * Opens a checkout for the budget total.
     * @param referenciaExterna our own identifier of the charge; the provider echoes it on every payment of the
     *                          checkout, which is how notifications are tied back to a {@code Pagamento}
     * @throws FalhaCobrancaException if the provider is unreachable or refuses the request
     */
    Cobranca criar(String referenciaExterna, UUID osId, UUID orcamentoId, BigDecimal valor);

    /**
     * Current state of the charge according to the provider: approved if any payment of the checkout was approved,
     * rejected if it has payments and all were rejected or cancelled, pending otherwise.
     * @throws FalhaCobrancaException if the provider is unreachable or refuses the request
     */
    EstadoPagamento consultar(String referenciaExterna);

    /**
     * What the provider says about one payment, as reported by a notification.
     * @return empty when the provider does not know the payment id
     * @throws FalhaCobrancaException if the provider is unreachable or refuses the request
     */
    Optional<PagamentoProvedor> consultarPagamento(String paymentId);

    /** @param referenciaExterna the reference sent in {@link #criar}; null when the payment is not ours */
    record PagamentoProvedor(String referenciaExterna, EstadoPagamento estado) { }

    record Cobranca(String referenciaExterna, String linkPagamento) { }
}
