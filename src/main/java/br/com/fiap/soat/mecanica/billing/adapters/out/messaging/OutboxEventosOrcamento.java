package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.application.orcamento.EventosOrcamento;
import br.com.fiap.soat.mecanica.billing.application.orcamento.MotivoRecusa;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class OutboxEventosOrcamento implements EventosOrcamento {
    static final String GERADO = "OrcamentoGerado";
    static final String APROVADO = "OrcamentoAprovado";
    static final String RECUSADO = "OrcamentoRecusado";

    record Decisao(UUID osId, UUID orcamentoId) { }

    /** Extra field the OS may ignore: its mapper skips unknown properties. */
    record Recusa(UUID osId, UUID orcamentoId, MotivoRecusa motivo) { }

    private final OutboxEnvelopes envelopes;

    OutboxEventosOrcamento(OutboxEnvelopes envelopes) { this.envelopes = envelopes; }

    @Override
    public void gerado(Orcamento orcamento, UUID sagaId, UUID causationId, String correlationId, Instant ocorridoEm) {
        registrar(GERADO, orcamento, sagaId, causationId, correlationId, ocorridoEm);
    }

    @Override
    public void aprovado(Orcamento orcamento, String correlationId, Instant ocorridoEm) {
        registrar(APROVADO, orcamento, null, null, correlationId, ocorridoEm);
    }

    @Override
    public void recusado(Orcamento orcamento, MotivoRecusa motivo, String correlationId, Instant ocorridoEm) {
        registrar(RECUSADO, orcamento, null, null, correlationId, ocorridoEm,
                new Recusa(orcamento.osId(), orcamento.id(), motivo));
    }

    private void registrar(String tipo, Orcamento orcamento, UUID sagaId, UUID causationId, String correlationId,
                           Instant ocorridoEm) {
        registrar(tipo, orcamento, sagaId, causationId, correlationId, ocorridoEm,
                new Decisao(orcamento.osId(), orcamento.id()));
    }

    private void registrar(String tipo, Orcamento orcamento, UUID sagaId, UUID causationId, String correlationId,
                           Instant ocorridoEm, Object payload) {
        // Decisions made through the API have no saga or causation yet: the command that carried them is
        // not stored with the budget. The OS finds the Saga by osId.
        envelopes.registrar(tipo, orcamento.osId(), sagaId, causationId, correlationId, ocorridoEm, payload);
    }
}
