package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import java.util.List;
import java.util.Map;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

/** SNS topic with one raw-delivery SQS subscriber, the way the OS service subscribes to Billing. */
final class LocalStackSupport {
    static final LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.9.2"))
            .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

    static { localstack.start(); }

    static final StaticCredentialsProvider CREDENCIAIS =
            StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test"));
    static final SnsClient sns = SnsClient.builder().endpointOverride(localstack.getEndpoint())
            .region(Region.of(localstack.getRegion())).credentialsProvider(CREDENCIAIS).build();
    static final SqsClient sqs = SqsClient.builder().endpointOverride(localstack.getEndpoint())
            .region(Region.of(localstack.getRegion())).credentialsProvider(CREDENCIAIS).build();

    record Canal(String topicArn, String queueUrl) { }

    private LocalStackSupport() { }

    static Canal novoCanal(String nome) {
        String topicArn = sns.createTopic(b -> b.name(nome + "-eventos")).topicArn();
        String queueUrl = sqs.createQueue(b -> b.queueName(nome + "-respostas")).queueUrl();
        String queueArn = sqs.getQueueAttributes(b -> b.queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)).attributes().get(QueueAttributeName.QUEUE_ARN);
        sns.subscribe(SubscribeRequest.builder().topicArn(topicArn).protocol("sqs").endpoint(queueArn)
                .attributes(Map.of("RawMessageDelivery", "true")).build());
        return new Canal(topicArn, queueUrl);
    }

    static List<Message> receber(String queueUrl, int segundos) {
        return sqs.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(10)
                .waitTimeSeconds(segundos).messageAttributeNames("All")).messages();
    }
}
