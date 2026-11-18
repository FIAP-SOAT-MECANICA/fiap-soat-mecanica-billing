package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes budgets nobody decided in time. The Saga has no "expired" event, and a budget left GERADO would keep it
 * waiting forever, so an expiry is announced as OrcamentoRecusado: the OS cancels the order, as it does when the
 * customer refuses. The state in this service stays EXPIRADO, which is how the two cases are told apart.
 */
@Service
public class ExpirarOrcamentosUseCase {
    private final OrcamentoRepository orcamentos;
    private final EventosOrcamento eventos;
    private final Clock relogio;

    public ExpirarOrcamentosUseCase(OrcamentoRepository orcamentos, EventosOrcamento eventos, Clock relogio) {
        this.orcamentos = orcamentos;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /**
     * State changes and events of the batch commit together. The rows are locked while it runs, and a budget
     * approved in the meantime fails the version check, undoing the batch; the next run no longer selects it.
     * @return how many budgets expired
     */
    @Transactional
    public int executar(int limite) {
        Instant agora = relogio.instant();
        var vencidos = orcamentos.buscarGeradosVencidos(agora, limite);
        for (var registro : vencidos) {
            var expirado = orcamentos.atualizar(registro.orcamento().expirar(agora), registro.versao()).orcamento();
            eventos.recusado(expirado, null, agora);
        }
        return vencidos.size();
    }
}
