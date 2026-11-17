package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.application.orcamento.EventosOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes budget decisions to the outbox in the message envelope shared by the services (RFC-002 of the OS). */
@Component
class OutboxEventosOrcamento implements EventosOrcamento {
    static final String APROVADO = "OrcamentoAprovado";
    static final String RECUSADO = "OrcamentoRecusado";

    record Envelope(UUID messageId, String type, int version, UUID sagaId, UUID osId, String correlationId,
                    UUID causationId, Instant occurredAt, Object payload) { }

    record Decisao(UUID osId, UUID orcamentoId) { }

    private final OutboxRepository outbox;
    private final ObjectMapper mapper;

    OutboxEventosOrcamento(OutboxRepository outbox, ObjectMapper mapper) {
        this.outbox = outbox;
        this.mapper = mapper;
    }

    @Override
    public void aprovado(Orcamento orcamento, String correlationId, Instant ocorridoEm) {
        registrar(APROVADO, orcamento, correlationId, ocorridoEm);
    }

    @Override
    public void recusado(Orcamento orcamento, String correlationId, Instant ocorridoEm) {
        registrar(RECUSADO, orcamento, correlationId, ocorridoEm);
    }

    private void registrar(String tipo, Orcamento orcamento, String correlationId, Instant ocorridoEm) {
        UUID messageId = UUID.randomUUID();
        // sagaId and causationId stay null until the GerarOrcamento command, which carries them,
        // is consumed and stored with the budget; the OS can still find the Saga by osId.
        var envelope = new Envelope(messageId, tipo, 1, null, orcamento.osId(), correlationId, null, ocorridoEm,
                new Decisao(orcamento.osId(), orcamento.id()));
        try {
            outbox.registrar(messageId, tipo, mapper.writeValueAsString(envelope), ocorridoEm);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("evento " + tipo + " nao serializavel", e);
        }
    }
}
