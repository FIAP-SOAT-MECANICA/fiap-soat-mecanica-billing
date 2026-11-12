package br.com.fiap.soat.mecanica.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable aggregate. Commands return a new state for transactional persistence. */
public record Orcamento(UUID id, UUID osId, UUID clienteId, List<ItemOrcamento> itens,
                        BigDecimal valorTotal, EstadoOrcamento estado,
                        Instant criadoEm, Instant expiraEm) {
    public Orcamento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(osId, "osId");
        Objects.requireNonNull(clienteId, "clienteId");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(criadoEm, "criadoEm");
        Objects.requireNonNull(expiraEm, "expiraEm");
        if (!expiraEm.isAfter(criadoEm)) throw new IllegalArgumentException("expiracao invalida");
        itens = List.copyOf(Objects.requireNonNull(itens, "itens"));
        if (itens.isEmpty()) throw new IllegalArgumentException("orcamento sem itens");
        var itemIds = new HashSet<UUID>();
        var servicoIds = new HashSet<UUID>();
        for (ItemOrcamento item : itens) {
            Objects.requireNonNull(item, "item");
            if (!itemIds.add(item.itemId()) || !servicoIds.add(item.servicoId()))
                throw new IllegalArgumentException("item ou servico duplicado");
        }
        BigDecimal calculated = itens.stream().map(ItemOrcamento::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        if (calculated.signum() <= 0 || valorTotal == null || calculated.compareTo(valorTotal) != 0)
            throw new IllegalArgumentException("valorTotal divergente ou invalido");
        valorTotal = calculated;
    }

    public static Orcamento gerar(UUID id, UUID osId, UUID clienteId, List<ItemOrcamento> itens,
                                  Instant criadoEm, Instant expiraEm) {
        BigDecimal total = Objects.requireNonNull(itens, "itens").stream()
                .map(ItemOrcamento::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Orcamento(id, osId, clienteId, itens, total, EstadoOrcamento.GERADO, criadoEm, expiraEm);
    }

    public Orcamento aprovar(Instant agora) {
        requireGerado();
        if (!Objects.requireNonNull(agora, "agora").isBefore(expiraEm))
            throw new IllegalStateException("orcamento expirado");
        return withEstado(EstadoOrcamento.APROVADO);
    }

    public Orcamento recusar() { requireGerado(); return withEstado(EstadoOrcamento.RECUSADO); }

    public Orcamento expirar(Instant agora) {
        requireGerado();
        if (Objects.requireNonNull(agora, "agora").isBefore(expiraEm))
            throw new IllegalStateException("orcamento ainda vigente");
        return withEstado(EstadoOrcamento.EXPIRADO);
    }

    public Orcamento cancelar() {
        if (estado != EstadoOrcamento.GERADO && estado != EstadoOrcamento.APROVADO)
            throw new IllegalStateException("orcamento nao pode ser cancelado");
        return withEstado(EstadoOrcamento.CANCELADO);
    }

    private void requireGerado() {
        if (estado != EstadoOrcamento.GERADO) throw new IllegalStateException("orcamento nao esta gerado");
    }

    private Orcamento withEstado(EstadoOrcamento novo) {
        return new Orcamento(id, osId, clienteId, itens, valorTotal, novo, criadoEm, expiraEm);
    }
}
