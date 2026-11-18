package br.com.fiap.soat.mecanica.billing.adapters.in.scheduling;

import br.com.fiap.soat.mecanica.billing.application.orcamento.ExpirarOrcamentosUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** On by default; {@code billing.expiracao.habilitada=false} turns the job off. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "billing.expiracao.habilitada", havingValue = "true", matchIfMissing = true)
class ExpiracaoConfig {
    @Bean
    ExpiracaoScheduler expiracaoScheduler(ExpirarOrcamentosUseCase expirar, @Value("${billing.expiracao.lote}") int lote) {
        return new ExpiracaoScheduler(expirar, lote);
    }
}
