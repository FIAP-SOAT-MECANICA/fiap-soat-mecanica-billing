package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;

/**
 * Reads the command queue. A message is deleted only after its transaction committed, so a crash in between
 * redelivers it, and the inbox makes the redelivery harmless (at-least-once).
 */
public class SqsConsumidorComandos {
    private static final Logger log = LoggerFactory.getLogger(SqsConsumidorComandos.class);

    private final SqsClient sqs;
    private final String filaUrl;
    private final TratadorComandos tratador;
    private final int esperaSegundos;

    public SqsConsumidorComandos(SqsClient sqs, String filaUrl, TratadorComandos tratador, int esperaSegundos) {
        this.sqs = sqs;
        this.filaUrl = filaUrl;
        this.tratador = tratador;
        this.esperaSegundos = esperaSegundos;
    }

    @Scheduled(fixedDelayString = "${billing.comandos.intervalo-ms}")
    public void consumir() {
        try {
            sqs.receiveMessage(b -> b.queueUrl(filaUrl).maxNumberOfMessages(10).waitTimeSeconds(esperaSegundos))
                    .messages().forEach(this::processar);
        } catch (SdkException e) {
            // Broker unreachable: try again on the next tick instead of killing the scheduler thread.
            log.error("Falha ao ler a fila de comandos", e);
        }
    }

    void processar(Message mensagem) {
        try {
            if (tratador.tratar(mensagem.body()))
                sqs.deleteMessage(b -> b.queueUrl(filaUrl).receiptHandle(mensagem.receiptHandle()));
        } catch (Exception e) {
            log.error("Falha ao processar comando; ele volta para a fila. sqsMessageId={}", mensagem.messageId(), e);
        }
    }
}
