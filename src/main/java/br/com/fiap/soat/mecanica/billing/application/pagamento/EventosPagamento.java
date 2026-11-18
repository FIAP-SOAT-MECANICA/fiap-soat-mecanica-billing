package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.Pagamento;
import java.time.Instant;

/**
 * Announces the outcome of a charge to the Saga. Implementations record the event in the caller's
 * transaction, so it is published only if the state change commits.
 */
public interface EventosPagamento {
    void confirmado(Pagamento pagamento, String correlationId, Instant ocorridoEm);

    void recusado(Pagamento pagamento, String correlationId, Instant ocorridoEm);
}
