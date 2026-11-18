package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import br.com.fiap.soat.mecanica.billing.application.pagamento.ProcessarNotificacaoPagamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.pagamento.ProcessarNotificacaoPagamentoUseCase.Notificacao;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint called by Mercado Pago, authenticated only by the signature. Answers 2xx once the notification
 * was understood, even if it needed no action; any other status makes the provider retry.
 */
@RestController
@RequestMapping("/webhooks/mercadopago")
@Hidden
@ConditionalOnExpression("!'${billing.mercadopago.access-token:}'.isBlank()")
class WebhookMercadoPagoController {
    private static final Logger log = LoggerFactory.getLogger(WebhookMercadoPagoController.class);

    private final ProcessarNotificacaoPagamentoUseCase processar;
    private final AssinaturaMercadoPago assinatura;

    WebhookMercadoPagoController(ProcessarNotificacaoPagamentoUseCase processar,
                                 @Value("${billing.mercadopago.webhook-secret:}") String segredo) {
        this.processar = processar;
        this.assinatura = new AssinaturaMercadoPago(segredo);
        if (segredo.isBlank()) log.warn("MERCADOPAGO_WEBHOOK_SECRET ausente: toda notificacao sera recusada");
    }

    @PostMapping
    ResponseEntity<Void> receber(@RequestParam(name = "data.id", required = false) String dataId,
                                 @RequestHeader(name = "x-signature", required = false) String xSignature,
                                 @RequestHeader(name = "x-request-id", required = false) String xRequestId,
                                 @RequestBody JsonNode corpo) {
        // The id comes from the query string because that is what the provider signs.
        if (!assinatura.valida(xSignature, xRequestId, dataId)) return ResponseEntity.status(401).build();

        if (!"payment".equals(corpo.path("type").asText())) return ResponseEntity.ok().build();
        String eventoId = corpo.path("id").asText("");
        if (eventoId.isBlank()) eventoId = dataId + ":" + corpo.path("action").asText("");
        processar.executar(new Notificacao(eventoId, dataId, xRequestId));
        return ResponseEntity.ok().build();
    }
}
