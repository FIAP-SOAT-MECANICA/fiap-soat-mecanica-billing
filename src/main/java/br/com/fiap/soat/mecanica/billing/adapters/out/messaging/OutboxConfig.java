package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.application.outbox.EsvaziarOutboxUseCase;
import br.com.fiap.soat.mecanica.billing.application.outbox.PublicadorEventos;
import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import java.net.URI;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
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
        var builder = SnsClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        // Without static keys the default provider chain applies (environment, instance role).
        if (!accessKey.isBlank() && !secretKey.isBlank())
            builder.credentialsProvider(StaticCredentialsProvider.create(credenciais(accessKey, secretKey, sessionToken)));
        return builder.build();
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

    private static AwsCredentials credenciais(String accessKey, String secretKey, String sessionToken) {
        return sessionToken.isBlank() ? AwsBasicCredentials.create(accessKey, secretKey)
                : AwsSessionCredentials.create(accessKey, secretKey, sessionToken);
    }
}
