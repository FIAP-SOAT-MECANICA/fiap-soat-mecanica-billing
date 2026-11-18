package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes an event to the outbox in the message envelope shared by the services (RFC-002 of the OS). */
@Component
class OutboxEnvelopes {
    record Envelope(UUID messageId, String type, int version, UUID sagaId, UUID osId, String correlationId,
                    UUID causationId, Instant occurredAt, Object payload) { }

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    OutboxEnvelopes(OutboxRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    /** Joins the caller's transaction, so the event is published only if the state change commits. */
    void registrar(String tipo, UUID osId, UUID sagaId, UUID causationId, String correlationId, Instant ocorridoEm,
                   Object payload) {
        UUID messageId = UUID.randomUUID();
        var envelope = new Envelope(messageId, tipo, 1, sagaId, osId, correlationId, causationId, ocorridoEm, payload);
        try {
            outbox.registrar(messageId, tipo, mapper.writeValueAsString(envelope), ocorridoEm);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("evento " + tipo + " nao serializavel", e);
        }
    }
}
