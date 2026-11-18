package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.AcessoNegadoException;
import br.com.fiap.soat.mecanica.billing.application.Solicitante;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository.Registro;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsultarPagamentoUseCase {
    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;

    public ConsultarPagamentoUseCase(OrcamentoRepository orcamentos, PagamentoRepository pagamentos) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
    }

    /** Charge attempts of the OS, newest first, each with the link where the customer pays; empty if none. */
    public List<Registro> porOs(UUID osId, Solicitante solicitante) {
        // The owner of a charge is the customer of its budget; an OS without a budget has nothing to hide.
        orcamentos.listarPorOs(osId).forEach(registro -> {
            if (!solicitante.podeAcessar(registro.orcamento()))
                throw new AcessoNegadoException("Pagamento pertence a outro cliente");
        });
        return pagamentos.listarPorOs(osId);
    }
}
