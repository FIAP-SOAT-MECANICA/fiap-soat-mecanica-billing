package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PagamentoResponse(UUID id, UUID orcamentoId, UUID osId, BigDecimal valor, EstadoPagamento estado,
                         String linkPagamento, Instant criadoEm, Instant decididoEm) {
    static PagamentoResponse de(Registro registro) {
        var p = registro.pagamento();
        return new PagamentoResponse(p.id(), p.orcamentoId(), p.osId(), p.valor(), p.estado(),
                registro.linkPagamento(), p.criadoEm(), p.decididoEm());
    }
}
