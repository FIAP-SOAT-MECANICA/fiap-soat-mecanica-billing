package br.com.fiap.soat.mecanica.billing.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Events recorded in the same transaction as the state change and published after commit. */
public interface OutboxRepository {
    record Pendente(UUID eventId, String tipo, String envelope, Instant criadoEm) { }

    /** @param envelope the message envelope as a JSON document */
    void registrar(UUID eventId, String tipo, String envelope, Instant criadoEm);

    /**
     * Oldest unpublished events. Inside a transaction the returned rows stay locked until it
     * ends and rows locked by another publisher are skipped, so two publishers never share one.
     */
    List<Pendente> buscarPendentes(int limite);

    void marcarPublicado(UUID eventId, Instant publicadoEm);
}
