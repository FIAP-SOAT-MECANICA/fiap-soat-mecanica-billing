package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.application.pagamento.EventosPagamento;
import br.com.fiap.soat.mecanica.billing.domain.Pagamento;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class OutboxEventosPagamento implements EventosPagamento {
    static final String CONFIRMADO = "PagamentoConfirmado";
    static final String RECUSADO = "PagamentoRecusado";

    record Decisao(UUID osId, UUID orcamentoId, UUID pagamentoId) { }

    private final OutboxEnvelopes envelopes;

    OutboxEventosPagamento(OutboxEnvelopes envelopes) { this.envelopes = envelopes; }

    @Override
    public void confirmado(Pagamento pagamento, String correlationId, Instant ocorridoEm) {
        registrar(CONFIRMADO, pagamento, correlationId, ocorridoEm);
    }

    @Override
    public void recusado(Pagamento pagamento, String correlationId, Instant ocorridoEm) {
        registrar(RECUSADO, pagamento, correlationId, ocorridoEm);
    }

    private void registrar(String tipo, Pagamento pagamento, String correlationId, Instant ocorridoEm) {
        // The saga id is not stored with the charge; the OS finds the Saga by osId.
        envelopes.registrar(tipo, pagamento.osId(), null, null, correlationId, ocorridoEm,
                new Decisao(pagamento.osId(), pagamento.orcamentoId(), pagamento.id()));
    }
}
