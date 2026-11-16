package br.com.fiap.soat.mecanica.billing.domain;

import java.time.Instant;
import java.util.UUID;

/** Inbox for at-least-once delivery; call it in the same transaction as the business effect. */
public interface MensagemProcessadaRepository {
    /** @return true the first time a message id is seen, false when it is a redelivery */
    boolean registrar(UUID messageId, Instant processadaEm);
}
