package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.application.outbox.EsvaziarOutboxUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final EsvaziarOutboxUseCase esvaziar;
    private final int lote;

    OutboxScheduler(EsvaziarOutboxUseCase esvaziar, int lote) {
        this.esvaziar = esvaziar;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${billing.outbox.intervalo-ms}")
    void publicarPendentes() {
        try {
            int publicados = esvaziar.executar(lote);
            if (publicados > 0) log.info("{} evento(s) publicado(s) no SNS", publicados);
        } catch (RuntimeException e) {
            // Database trouble must not kill the schedule; the next tick tries again.
            log.error("Falha ao esvaziar a outbox", e);
        }
    }
}
