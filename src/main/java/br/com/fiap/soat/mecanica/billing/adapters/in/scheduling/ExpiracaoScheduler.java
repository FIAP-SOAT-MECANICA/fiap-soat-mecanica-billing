package br.com.fiap.soat.mecanica.billing.adapters.in.scheduling;

import br.com.fiap.soat.mecanica.billing.application.orcamento.ExpirarOrcamentosUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class ExpiracaoScheduler {
    private static final Logger log = LoggerFactory.getLogger(ExpiracaoScheduler.class);

    private final ExpirarOrcamentosUseCase expirar;
    private final int lote;

    public ExpiracaoScheduler(ExpirarOrcamentosUseCase expirar, int lote) {
        this.expirar = expirar;
        this.lote = lote;
    }

    @Scheduled(fixedDelayString = "${billing.expiracao.intervalo-ms}")
    public void expirarVencidos() {
        try {
            int expirados = expirar.executar(lote);
            if (expirados > 0) log.info("{} orcamento(s) expirado(s)", expirados);
        } catch (RuntimeException e) {
            // Database trouble must not kill the schedule; the next tick tries again.
            log.error("Falha ao expirar orcamentos", e);
        }
    }
}
