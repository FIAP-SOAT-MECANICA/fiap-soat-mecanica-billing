package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemPeca;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record OrcamentoResponse(UUID id, UUID osId, UUID clienteId, int revisao, EstadoOrcamento estado,
                         BigDecimal valorTotal, List<Item> itens, Instant criadoEm, Instant expiraEm) {
    record Item(UUID itemId, UUID servicoId, String nomeServico, BigDecimal precoMdo, List<Peca> pecas,
                BigDecimal total) {
        static Item de(ItemOrcamento item) {
            return new Item(item.itemId(), item.servicoId(), item.nomeServico(), item.precoMdo(),
                    item.pecas().stream().map(Peca::de).toList(), item.total());
        }
    }

    record Peca(UUID pecaId, int quantidade, BigDecimal valorUnitario, BigDecimal total) {
        static Peca de(ItemPeca peca) {
            return new Peca(peca.pecaId(), peca.quantidade(), peca.valorUnitario(), peca.total());
        }
    }

    static OrcamentoResponse de(Registro registro) {
        Orcamento o = registro.orcamento();
        return new OrcamentoResponse(o.id(), o.osId(), o.clienteId(), registro.revisao(), o.estado(),
                o.valorTotal(), o.itens().stream().map(Item::de).toList(), o.criadoEm(), o.expiraEm());
    }
}
