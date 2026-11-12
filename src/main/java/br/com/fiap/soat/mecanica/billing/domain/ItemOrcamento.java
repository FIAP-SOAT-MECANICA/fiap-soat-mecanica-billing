package br.com.fiap.soat.mecanica.billing.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable service snapshot from the completed diagnosis. */
public record ItemOrcamento(UUID itemId, UUID servicoId, String nomeServico,
                            BigDecimal precoMdo, List<ItemPeca> pecas) {
    public ItemOrcamento {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(servicoId, "servicoId");
        if (nomeServico == null || nomeServico.isBlank()) throw new IllegalArgumentException("nomeServico obrigatorio");
        if (precoMdo == null || precoMdo.signum() < 0) throw new IllegalArgumentException("precoMdo invalido");
        precoMdo = precoMdo.setScale(2, java.math.RoundingMode.UNNECESSARY);
        pecas = List.copyOf(Objects.requireNonNull(pecas, "pecas"));
        var ids = new HashSet<UUID>();
        for (ItemPeca peca : pecas) {
            Objects.requireNonNull(peca, "peca");
            if (!ids.add(peca.pecaId())) throw new IllegalArgumentException("peca duplicada no item");
        }
        if (precoMdo.signum() == 0 && pecas.isEmpty())
            throw new IllegalArgumentException("item sem valor");
    }

    public BigDecimal total() {
        return pecas.stream().map(ItemPeca::total).reduce(precoMdo, BigDecimal::add);
    }
}
