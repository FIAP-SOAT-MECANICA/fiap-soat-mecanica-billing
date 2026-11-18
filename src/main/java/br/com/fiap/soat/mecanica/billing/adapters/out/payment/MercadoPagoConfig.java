package br.com.fiap.soat.mecanica.billing.adapters.out.payment;

import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Everything here exists only when {@code billing.mercadopago.access-token} is set. */
@Configuration
@ConditionalOnExpression("!'${billing.mercadopago.access-token:}'.isBlank()")
class MercadoPagoConfig {
    @Bean
    CobrancaGateway cobrancaGateway(@Value("${billing.mercadopago.base-url}") String baseUrl,
                                    @Value("${billing.mercadopago.access-token}") String accessToken,
                                    @Value("${billing.mercadopago.notification-url:}") String notificationUrl,
                                    @Value("${billing.mercadopago.timeout-segundos}") int timeoutSegundos) {
        var fabrica = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSegundos)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        return new MercadoPagoCobrancaGateway(RestClient.builder().requestFactory(fabrica), baseUrl, accessToken,
                notificationUrl);
    }
}
