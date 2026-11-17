package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import java.time.Instant;

/**
 * Announces budget decisions to the Saga. Implementations record the event in the caller's
 * transaction, so it is published only if the state change commits.
 */
public interface EventosOrcamento {
    void aprovado(Orcamento orcamento, String correlationId, Instant ocorridoEm);

    void recusado(Orcamento orcamento, String correlationId, Instant ocorridoEm);
}
