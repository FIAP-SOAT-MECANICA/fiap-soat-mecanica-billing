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
    static final String GERADO = "OrcamentoGerado";
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
    public void gerado(Orcamento orcamento, UUID sagaId, UUID causationId, String correlationId, Instant ocorridoEm) {
        registrar(GERADO, orcamento, sagaId, causationId, correlationId, ocorridoEm);
    }

    @Override
    public void aprovado(Orcamento orcamento, String correlationId, Instant ocorridoEm) {
        registrar(APROVADO, orcamento, null, null, correlationId, ocorridoEm);
    }

    @Override
    public void recusado(Orcamento orcamento, String correlationId, Instant ocorridoEm) {
        registrar(RECUSADO, orcamento, null, null, correlationId, ocorridoEm);
    }

    private void registrar(String tipo, Orcamento orcamento, UUID sagaId, UUID causationId, String correlationId,
                           Instant ocorridoEm) {
        UUID messageId = UUID.randomUUID();
        // Decisions made through the API have no saga or causation yet: the command that carried them is
        // not stored with the budget. The OS finds the Saga by osId.
        var envelope = new Envelope(messageId, tipo, 1, sagaId, orcamento.osId(), correlationId, causationId, ocorridoEm,
                new Decisao(orcamento.osId(), orcamento.id()));
        try {
            outbox.registrar(messageId, tipo, mapper.writeValueAsString(envelope), ocorridoEm);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("evento " + tipo + " nao serializavel", e);
        }
    }
}
