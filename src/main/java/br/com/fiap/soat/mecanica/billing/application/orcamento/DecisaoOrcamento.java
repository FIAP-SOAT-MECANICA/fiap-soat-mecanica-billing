package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.application.Solicitante;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/** Steps shared by approval and refusal; runs inside the transaction of the calling use case. */
@Component
class DecisaoOrcamento {
    private final OrcamentoRepository orcamentos;

    DecisaoOrcamento(OrcamentoRepository orcamentos) { this.orcamentos = orcamentos; }

    Registro aplicar(UUID id, Solicitante solicitante, UnaryOperator<Orcamento> transicao,
                     Consumer<Orcamento> evento) {
        Registro lido = orcamentos.buscarPorId(id).orElseThrow(() -> new OrcamentoNaoEncontradoException(id));
        ConsultarOrcamentoUseCase.exigirAcesso(lido, solicitante);
        Orcamento decidido;
        try {
            decidido = transicao.apply(lido.orcamento());
        } catch (IllegalStateException e) {
            throw new TransicaoInvalidaException("Decisão rejeitada: " + e.getMessage(), e);
        }
        // The version read above makes a concurrent decision fail instead of being overwritten.
        Registro gravado = orcamentos.atualizar(decidido, lido.versao());
        evento.accept(gravado.orcamento());
        return gravado;
    }
}
