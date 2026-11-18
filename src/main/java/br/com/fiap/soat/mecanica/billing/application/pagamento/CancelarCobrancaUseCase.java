package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.MensagemProcessadaRepository;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository.Registro;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CancelarCobranca command, the Saga's compensation after a refused payment. Only a charge nobody decided yet
 * is withdrawn; anything else (none, refused, paid, already cancelled) is logged and acknowledged, because failing
 * would block the compensation in the dead-letter queue. The checkout page at the provider is not closed.
 */
@Service
public class CancelarCobrancaUseCase {
    private static final Logger log = LoggerFactory.getLogger(CancelarCobrancaUseCase.class);

    /** {@code orcamentoId} may be null; then every undecided charge of the OS is withdrawn. */
    public record Comando(UUID messageId, UUID osId, UUID orcamentoId) { }

    private final MensagemProcessadaRepository inbox;
    private final PagamentoRepository pagamentos;
    private final Clock relogio;

    public CancelarCobrancaUseCase(MensagemProcessadaRepository inbox, PagamentoRepository pagamentos, Clock relogio) {
        this.inbox = inbox;
        this.pagamentos = pagamentos;
        this.relogio = relogio;
    }

    @Transactional
    public ResultadoComando executar(Comando comando) {
        Instant agora = relogio.instant();
        if (!inbox.registrar(comando.messageId(), agora)) return ResultadoComando.DUPLICADO;

        int cancelados = 0;
        for (Registro registro : pagamentos.listarPorOs(comando.osId())) {
            var pagamento = registro.pagamento();
            if (comando.orcamentoId() != null && !pagamento.orcamentoId().equals(comando.orcamentoId())) continue;
            if (pagamento.estado() != EstadoPagamento.PENDENTE) continue;
            pagamentos.atualizar(pagamento.cancelar(agora), registro.versao());
            cancelados++;
        }
        if (cancelados == 0)
            log.info("CancelarCobranca sem efeito: nenhuma cobranca pendente. osId={} orcamentoId={}",
                    comando.osId(), comando.orcamentoId());
        return ResultadoComando.PROCESSADO;
    }
}
