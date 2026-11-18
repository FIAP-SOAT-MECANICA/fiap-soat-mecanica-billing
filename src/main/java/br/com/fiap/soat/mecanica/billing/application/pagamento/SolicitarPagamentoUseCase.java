package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway;
import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.MensagemProcessadaRepository;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SolicitarPagamento command from the OS: opens the checkout of the approved budget. Exists only when the
 * payment provider is configured. The answer is not an event: the customer pays on the provider's page and the
 * webhook reports the outcome.
 */
@Service
@ConditionalOnExpression("!'${billing.mercadopago.access-token:}'.isBlank()")
public class SolicitarPagamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(SolicitarPagamentoUseCase.class);

    /** {@code orcamentoId} may be null when the OS never learned it; the latest budget of the OS is used then. */
    public record Comando(UUID messageId, UUID osId, UUID orcamentoId) { }

    private final MensagemProcessadaRepository inbox;
    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final CobrancaGateway gateway;
    private final Clock relogio;

    public SolicitarPagamentoUseCase(MensagemProcessadaRepository inbox, OrcamentoRepository orcamentos,
                                     PagamentoRepository pagamentos, CobrancaGateway gateway, Clock relogio) {
        this.inbox = inbox;
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
        this.gateway = gateway;
        this.relogio = relogio;
    }

    /**
     * A budget that cannot be charged (unknown, of another OS, not approved) is a saga error, so it throws and the
     * message ends in the dead-letter queue. A provider failure also throws: the redelivery retries.
     */
    @Transactional
    public ResultadoComando executar(Comando comando) {
        Instant agora = relogio.instant();
        if (!inbox.registrar(comando.messageId(), agora)) return ResultadoComando.DUPLICADO;

        Orcamento orcamento = (comando.orcamentoId() != null
                ? orcamentos.buscarPorId(comando.orcamentoId())
                : orcamentos.buscarUltimoPorOs(comando.osId()))
                .orElseThrow(() -> new IllegalStateException("orcamento nao encontrado: osId=" + comando.osId()
                        + " orcamentoId=" + comando.orcamentoId()))
                .orcamento();
        if (!orcamento.osId().equals(comando.osId()))
            throw new IllegalStateException("orcamento " + orcamento.id() + " pertence a outra OS");
        if (orcamento.estado() != EstadoOrcamento.APROVADO)
            throw new IllegalStateException("orcamento " + orcamento.id() + " nao esta aprovado: " + orcamento.estado());

        boolean emAndamentoOuPago = pagamentos.listarPorOs(comando.osId()).stream()
                .map(PagamentoRepository.Registro::pagamento)
                .filter(p -> p.orcamentoId().equals(orcamento.id()))
                .anyMatch(p -> p.estado() == EstadoPagamento.PENDENTE || p.estado() == EstadoPagamento.APROVADO);
        if (emAndamentoOuPago) {
            log.info("SolicitarPagamento sem efeito: orcamento {} ja tem cobranca pendente ou paga", orcamento.id());
            return ResultadoComando.PROCESSADO;
        }

        // Derived from the message id: if the commit fails after the provider accepted the call, the redelivery
        // asks for the same checkout again instead of leaving a second one behind.
        UUID id = UUID.nameUUIDFromBytes(("pagamento:" + comando.messageId()).getBytes(StandardCharsets.UTF_8));
        String referencia = id.toString();
        var cobranca = gateway.criar(referencia, orcamento.osId(), orcamento.id(), orcamento.valorTotal());
        pagamentos.inserir(Pagamento.criar(id, orcamento, referencia, agora), cobranca.linkPagamento());
        return ResultadoComando.PROCESSADO;
    }
}
