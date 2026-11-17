package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.application.Solicitante;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AprovarOrcamentoUseCase {
    private final DecisaoOrcamento decisao;
    private final EventosOrcamento eventos;
    private final Clock relogio;

    public AprovarOrcamentoUseCase(DecisaoOrcamento decisao, EventosOrcamento eventos, Clock relogio) {
        this.decisao = decisao;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    /** State change and event are committed together or not at all. */
    @Transactional
    public Registro executar(UUID id, Solicitante solicitante, String correlationId) {
        Instant agora = relogio.instant();
        return decisao.aplicar(id, solicitante, orcamento -> orcamento.aprovar(agora),
                orcamento -> eventos.aprovado(orcamento, correlationId, agora));
    }
}
