package br.com.fiap.soat.mecanica.billing.adapters;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;

/** Settings shared by every AWS client of the service. */
public final class AwsClientes {
    private AwsClientes() { }

    public static <B extends AwsClientBuilder<B, ?>> B configurar(B builder, String region, String endpoint,
                                                                  String accessKey, String secretKey,
                                                                  String sessionToken) {
        builder.region(Region.of(region));
        if (!endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        // Without static keys the default provider chain applies (environment, instance role).
        if (!accessKey.isBlank() && !secretKey.isBlank())
            builder.credentialsProvider(StaticCredentialsProvider.create(credenciais(accessKey, secretKey, sessionToken)));
        return builder;
    }

    private static AwsCredentials credenciais(String accessKey, String secretKey, String sessionToken) {
        return sessionToken.isBlank() ? AwsBasicCredentials.create(accessKey, secretKey)
                : AwsSessionCredentials.create(accessKey, secretKey, sessionToken);
    }
}
