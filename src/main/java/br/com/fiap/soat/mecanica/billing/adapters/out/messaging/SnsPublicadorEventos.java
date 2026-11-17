package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import br.com.fiap.soat.mecanica.billing.application.outbox.FalhaPublicacaoException;
import br.com.fiap.soat.mecanica.billing.application.outbox.PublicadorEventos;
import java.util.Map;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

/** The envelope is the SNS message body; subscribers use raw delivery, so they receive it unchanged. */
class SnsPublicadorEventos implements PublicadorEventos {
    private final SnsClient sns;
    private final String topicArn;

    SnsPublicadorEventos(SnsClient sns, String topicArn) {
        this.sns = sns;
        this.topicArn = topicArn;
    }

    @Override
    public void publicar(String tipo, String envelope) {
        try {
            sns.publish(PublishRequest.builder().topicArn(topicArn).message(envelope)
                    .messageAttributes(Map.of("type", MessageAttributeValue.builder()
                            .dataType("String").stringValue(tipo).build()))
                    .build());
        } catch (SdkException e) {
            throw new FalhaPublicacaoException("SNS recusou o evento " + tipo, e);
        }
    }
}
