package br.com.fiap.soat.mecanica.billing.adapters.out.payment;

import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MercadoPagoConfigTest {
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MercadoPagoConfig.class)
            .withPropertyValues("billing.mercadopago.base-url=https://mp.test", "billing.mercadopago.timeout-segundos=3");

    @Test void semTokenNaoExisteGateway() {
        runner.withPropertyValues("billing.mercadopago.access-token=").run(ctx ->
                assertTrue(ctx.getBeansOfType(CobrancaGateway.class).isEmpty()));
        runner.run(ctx -> assertTrue(ctx.getBeansOfType(CobrancaGateway.class).isEmpty()));
    }

    @Test void comTokenOGatewayDoMercadoPagoEhCriado() {
        runner.withPropertyValues("billing.mercadopago.access-token=TOKEN").run(ctx ->
                assertInstanceOf(MercadoPagoCobrancaGateway.class, ctx.getBean(CobrancaGateway.class)));
    }
}
