package br.com.fiap.soat.mecanica.billing.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Immutable price and quantity snapshot, independent of the current stock catalog. */
public record ItemPeca(UUID pecaId, int quantidade, BigDecimal valorUnitario) {
    public ItemPeca {
        Objects.requireNonNull(pecaId, "pecaId");
        if (quantidade <= 0) throw new IllegalArgumentException("quantidade deve ser positiva");
        if (valorUnitario == null || valorUnitario.signum() < 0)
            throw new IllegalArgumentException("valorUnitario invalido");
        valorUnitario = valorUnitario.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    public BigDecimal total() { return valorUnitario.multiply(BigDecimal.valueOf(quantidade)); }
}
