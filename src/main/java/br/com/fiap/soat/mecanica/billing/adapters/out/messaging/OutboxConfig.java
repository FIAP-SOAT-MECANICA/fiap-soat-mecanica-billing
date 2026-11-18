package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.adapters.AwsClientes;
import br.com.fiap.soat.mecanica.billing.application.outbox.EsvaziarOutboxUseCase;
import br.com.fiap.soat.mecanica.billing.application.outbox.PublicadorEventos;
import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sns.SnsClient;

/** Everything here exists only when {@code billing.eventos.topic-arn} is set. */
@Configuration
@EnableScheduling
@ConditionalOnExpression("!'${billing.eventos.topic-arn:}'.isBlank()")
class OutboxConfig {
    @Bean
    SnsClient snsClient(@Value("${billing.aws.region}") String region,
                        @Value("${billing.aws.endpoint:}") String endpoint,
                        @Value("${billing.aws.access-key:}") String accessKey,
                        @Value("${billing.aws.secret-key:}") String secretKey,
                        @Value("${billing.aws.session-token:}") String sessionToken) {
        return AwsClientes.configurar(SnsClient.builder(), region, endpoint, accessKey, secretKey, sessionToken)
                .build();
    }

    @Bean
    PublicadorEventos publicadorEventos(SnsClient sns, @Value("${billing.eventos.topic-arn}") String topicArn) {
        return new SnsPublicadorEventos(sns, topicArn);
    }

    @Bean
    EsvaziarOutboxUseCase esvaziarOutboxUseCase(OutboxRepository outbox, PublicadorEventos publicador, Clock relogio) {
        return new EsvaziarOutboxUseCase(outbox, publicador, relogio);
    }

    @Bean
    OutboxScheduler outboxScheduler(EsvaziarOutboxUseCase esvaziar, @Value("${billing.outbox.lote}") int lote) {
        return new OutboxScheduler(esvaziar, lote);
    }
}
