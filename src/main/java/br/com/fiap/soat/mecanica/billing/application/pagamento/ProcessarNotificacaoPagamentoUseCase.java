package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.WebhookEventoRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Webhook of the payment provider. The notification only says that a payment changed: the state is read back
 * from the provider, never taken from the request. Exists only when the provider is configured.
 */
@Service
@ConditionalOnExpression("!'${billing.mercadopago.access-token:}'.isBlank()")
public class ProcessarNotificacaoPagamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessarNotificacaoPagamentoUseCase.class);

    public enum Resultado { DECIDIDO, SEM_EFEITO, JA_PROCESSADA }

    public record Notificacao(String providerEventId, String paymentId, String correlationId) { }

    private final WebhookEventoRepository webhooks;
    private final PagamentoRepository pagamentos;
    private final CobrancaGateway gateway;
    private final EventosPagamento eventos;
    private final Clock relogio;

    public ProcessarNotificacaoPagamentoUseCase(WebhookEventoRepository webhooks, PagamentoRepository pagamentos,
                                                CobrancaGateway gateway, EventosPagamento eventos, Clock relogio) {
        this.webhooks = webhooks;
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * Receipt, decision and event commit together. A provider failure throws, undoing the receipt, so the
     * provider's own retry processes the notification again.
     */
    @Transactional
    public Resultado executar(Notificacao notificacao) {
        Instant agora = relogio.instant();
        if (!webhooks.registrarRecebimento(notificacao.providerEventId(), notificacao.paymentId(), agora))
            return Resultado.JA_PROCESSADA;

        Resultado resultado = decidir(notificacao, agora);
        webhooks.marcarProcessado(notificacao.providerEventId(), agora);
        return resultado;
    }

    private Resultado decidir(Notificacao notificacao, Instant agora) {
        var informado = gateway.consultarPagamento(notificacao.paymentId());
        if (informado.isEmpty() || informado.get().referenciaExterna() == null) {
            log.warn("Notificacao ignorada: pagamento {} desconhecido ou sem referencia nossa", notificacao.paymentId());
            return Resultado.SEM_EFEITO;
        }
        var provedor = informado.get();
        if (provedor.estado() == EstadoPagamento.PENDENTE) return Resultado.SEM_EFEITO;

        var registro = pagamentos.buscarPorReferenciaExterna(provedor.referenciaExterna()).orElse(null);
        if (registro == null) {
            log.warn("Notificacao ignorada: nenhuma cobranca com a referencia do pagamento {}", notificacao.paymentId());
            return Resultado.SEM_EFEITO;
        }
        var atual = registro.pagamento();
        if (atual.estado() == provedor.estado()) return Resultado.SEM_EFEITO; // Repeated notification.
        if (atual.estado() != EstadoPagamento.PENDENTE) {
            // For example an approval after the charge was cancelled or refused: the money moved but the Saga
            // already compensated. Needs a manual refund; nothing here can undo a decision.
            log.error("Decisao tardia do provedor sobre cobranca {} ja {}: provedor diz {}; requer conferencia manual",
                    atual.id(), atual.estado(), provedor.estado());
            return Resultado.SEM_EFEITO;
        }

        if (provedor.estado() == EstadoPagamento.APROVADO) {
            var decidido = atual.confirmar(agora);
            pagamentos.atualizar(decidido, registro.versao());
            eventos.confirmado(decidido, notificacao.correlationId(), agora);
        } else {
            var decidido = atual.recusar(agora);
            pagamentos.atualizar(decidido, registro.versao());
            eventos.recusado(decidido, notificacao.correlationId(), agora);
        }
        return Resultado.DECIDIDO;
    }
}
