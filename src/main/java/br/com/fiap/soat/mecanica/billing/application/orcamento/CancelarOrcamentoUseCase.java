package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.MensagemProcessadaRepository;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CancelarOrcamento command, the Saga's compensation. It is idempotent by nature: an unknown or already
 * final budget is logged and acknowledged, because failing would block the compensation in the dead-letter queue.
 */
@Service
public class CancelarOrcamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(CancelarOrcamentoUseCase.class);

    /** {@code orcamentoId} may be null when the OS never learned it; the latest budget of the OS is used then. */
    public record Comando(UUID messageId, UUID osId, UUID orcamentoId) { }

    private final MensagemProcessadaRepository inbox;
    private final OrcamentoRepository orcamentos;
    private final Clock relogio;

    public CancelarOrcamentoUseCase(MensagemProcessadaRepository inbox, OrcamentoRepository orcamentos, Clock relogio) {
        this.inbox = inbox;
        this.orcamentos = orcamentos;
        this.relogio = relogio;
    }

    @Transactional
    public ResultadoComando executar(Comando comando) {
        if (!inbox.registrar(comando.messageId(), relogio.instant())) return ResultadoComando.DUPLICADO;

        Optional<Registro> encontrado = comando.orcamentoId() != null
                ? orcamentos.buscarPorId(comando.orcamentoId())
                : orcamentos.buscarUltimoPorOs(comando.osId());
        if (encontrado.isEmpty()) {
            log.warn("CancelarOrcamento ignorado: orcamento nao encontrado. osId={} orcamentoId={}",
                    comando.osId(), comando.orcamentoId());
            return ResultadoComando.PROCESSADO;
        }
        Registro registro = encontrado.get();
        if (!registro.orcamento().osId().equals(comando.osId())) {
            log.warn("CancelarOrcamento ignorado: orcamento {} pertence a outra OS", registro.orcamento().id());
            return ResultadoComando.PROCESSADO;
        }
        EstadoOrcamento estado = registro.orcamento().estado();
        if (estado != EstadoOrcamento.GERADO && estado != EstadoOrcamento.APROVADO) {
            log.info("CancelarOrcamento sem efeito: orcamento {} ja esta {}", registro.orcamento().id(), estado);
            return ResultadoComando.PROCESSADO;
        }
        orcamentos.atualizar(registro.orcamento().cancelar(), registro.versao());
        return ResultadoComando.PROCESSADO;
    }
}
