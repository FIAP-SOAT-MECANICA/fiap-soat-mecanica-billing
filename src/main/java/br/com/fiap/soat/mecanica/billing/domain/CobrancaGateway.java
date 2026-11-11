package br.com.fiap.soat.mecanica.billing.domain;

import java.util.UUID;

/** Implemented by the Mercado Pago adapter in the integration phase. */
public interface CobrancaGateway {
    Cobranca criar(UUID osId, UUID orcamentoId, java.math.BigDecimal valor);
    EstadoPagamento consultar(String referenciaExterna);

    record Cobranca(String referenciaExterna, String linkPagamento) { }
}
