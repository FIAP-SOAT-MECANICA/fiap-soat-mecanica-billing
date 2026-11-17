package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.application.AcessoNegadoException;
import br.com.fiap.soat.mecanica.billing.application.Solicitante;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsultarOrcamentoUseCase {
    private final OrcamentoRepository orcamentos;

    public ConsultarOrcamentoUseCase(OrcamentoRepository orcamentos) { this.orcamentos = orcamentos; }

    public Registro porId(UUID id, Solicitante solicitante) {
        Registro registro = orcamentos.buscarPorId(id).orElseThrow(() -> new OrcamentoNaoEncontradoException(id));
        exigirAcesso(registro, solicitante);
        return registro;
    }

    /** Every revision of the OS, oldest first; empty when the OS has no budget yet. */
    public List<Registro> porOs(UUID osId, Solicitante solicitante) {
        List<Registro> revisoes = orcamentos.listarPorOs(osId);
        revisoes.forEach(registro -> exigirAcesso(registro, solicitante));
        return revisoes;
    }

    static void exigirAcesso(Registro registro, Solicitante solicitante) {
        if (!solicitante.podeAcessar(registro.orcamento()))
            throw new AcessoNegadoException("Orçamento pertence a outro cliente");
    }
}
