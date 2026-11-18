package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import br.com.fiap.soat.mecanica.billing.adapters.AwsClientes;
import br.com.fiap.soat.mecanica.billing.application.orcamento.CancelarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.GerarOrcamentoUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.services.sqs.SqsClient;

/** Everything here exists only when {@code billing.comandos.queue-url} is set. */
@Configuration
@EnableScheduling
@ConditionalOnExpression("!'${billing.comandos.queue-url:}'.isBlank()")
class ComandosConfig {
    @Bean
    SqsClient sqsClient(@Value("${billing.aws.region}") String region,
                        @Value("${billing.aws.endpoint:}") String endpoint,
                        @Value("${billing.aws.access-key:}") String accessKey,
                        @Value("${billing.aws.secret-key:}") String secretKey,
                        @Value("${billing.aws.session-token:}") String sessionToken) {
        return AwsClientes.configurar(SqsClient.builder(), region, endpoint, accessKey, secretKey, sessionToken)
                .build();
    }

    @Bean
    TratadorComandos tratadorComandos(ObjectMapper mapper, GerarOrcamentoUseCase gerar,
                                      CancelarOrcamentoUseCase cancelar) {
        return new TratadorComandos(mapper, gerar, cancelar);
    }

    @Bean
    SqsConsumidorComandos consumidorComandos(SqsClient sqs, TratadorComandos tratador,
                                             @Value("${billing.comandos.queue-url}") String filaUrl,
                                             @Value("${billing.comandos.espera-segundos}") int esperaSegundos) {
        return new SqsConsumidorComandos(sqs, filaUrl, tratador, esperaSegundos);
    }
}
