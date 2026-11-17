package br.com.fiap.soat.mecanica.billing.application.outbox;

import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes pending outbox events, oldest first. Delivery is at least once: if the process dies
 * after the broker accepted a message but before the commit, the event goes out again, and
 * consumers deduplicate by {@code messageId}. Registered as a bean by the outbox configuration.
 */
public class EsvaziarOutboxUseCase {
    private static final Logger log = LoggerFactory.getLogger(EsvaziarOutboxUseCase.class);

    private final OutboxRepository outbox;
    private final PublicadorEventos publicador;
    private final Clock relogio;

    public EsvaziarOutboxUseCase(OutboxRepository outbox, PublicadorEventos publicador, Clock relogio) {
        this.outbox = outbox;
        this.publicador = publicador;
        this.relogio = relogio;
    }

    /**
     * The rows stay locked until the transaction ends, so concurrent instances never publish the
     * same event. A failure stops the batch to keep the order; what was already published is kept.
     *
     * @return how many events were published
     */
    @Transactional
    public int executar(int limite) {
        int publicados = 0;
        for (var pendente : outbox.buscarPendentes(limite)) {
            try {
                publicador.publicar(pendente.tipo(), pendente.envelope());
            } catch (FalhaPublicacaoException e) {
                log.error("Falha ao publicar {} ({}); o restante do lote fica para a proxima tentativa",
                        pendente.tipo(), pendente.eventId(), e);
                break;
            }
            outbox.marcarPublicado(pendente.eventId(), relogio.instant());
            publicados++;
        }
        return publicados;
    }
}
