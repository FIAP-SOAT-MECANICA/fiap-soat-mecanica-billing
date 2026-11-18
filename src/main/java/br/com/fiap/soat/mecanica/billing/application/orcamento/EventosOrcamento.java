package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import java.time.Instant;
import java.util.UUID;

/**
 * Announces budget decisions to the Saga. Implementations record the event in the caller's
 * transaction, so it is published only if the state change commits.
 */
public interface EventosOrcamento {
    /** The budget answers GerarOrcamento; saga and causation come from the command, so they are known. */
    void gerado(Orcamento orcamento, UUID sagaId, UUID causationId, String correlationId, Instant ocorridoEm);

    void aprovado(Orcamento orcamento, String correlationId, Instant ocorridoEm);

    void recusado(Orcamento orcamento, MotivoRecusa motivo, String correlationId, Instant ocorridoEm);
}
