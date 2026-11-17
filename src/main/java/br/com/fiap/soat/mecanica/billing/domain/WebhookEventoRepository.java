package br.com.fiap.soat.mecanica.billing.domain;

import java.time.Instant;

/** Provider notifications, deduplicated by the provider's own event id. */
public interface WebhookEventoRepository {
    /**
     * Records the notification if it is new. Inside a transaction the event stays locked until
     * the transaction ends, so a concurrent redelivery waits and then sees the outcome.
     * @return true while the event still has to be processed, false once it was processed
     */
    boolean registrarRecebimento(String providerEventId, String paymentId, Instant recebidoEm);

    void marcarProcessado(String providerEventId, Instant processadoEm);
}
