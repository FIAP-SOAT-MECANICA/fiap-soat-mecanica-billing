package br.com.fiap.soat.mecanica.billing.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrcamentoTest {
    private static final Instant CRIADO = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EXPIRA = Instant.parse("2026-01-02T10:00:00Z");

    private static ItemOrcamento item() {
        return new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Troca de filtro",
                new BigDecimal("80.00"), List.of(new ItemPeca(UUID.randomUUID(), 2, new BigDecimal("29.90"))));
    }

    private static Orcamento orcamento() {
        return Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(item()), CRIADO, EXPIRA);
    }

    @Test void conservaSnapshotECalculaTotalDecimal() {
        var mutable = new java.util.ArrayList<>(List.of(item()));
        var budget = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                mutable, CRIADO, EXPIRA);
        mutable.clear();
        assertEquals(new BigDecimal("139.80"), budget.valorTotal());
        assertEquals(1, budget.itens().size());
        assertThrows(UnsupportedOperationException.class, () -> budget.itens().clear());
        assertEquals(EstadoOrcamento.GERADO, budget.estado());
    }

    @Test void aprovaSomenteGeradoAntesDoPrazo() {
        var budget = orcamento();
        assertEquals(EstadoOrcamento.APROVADO, budget.aprovar(EXPIRA.minusNanos(1)).estado());
        assertEquals(EstadoOrcamento.GERADO, budget.estado());
        assertThrows(IllegalStateException.class, () -> budget.aprovar(EXPIRA));
        assertThrows(IllegalStateException.class, () -> budget.aprovar(CRIADO).aprovar(CRIADO));
    }

    @Test void recusaExpiraECancelaComRegras() {
        var budget = orcamento();
        assertEquals(EstadoOrcamento.RECUSADO, budget.recusar().estado());
        assertThrows(IllegalStateException.class, () -> budget.recusar().recusar());
        assertThrows(IllegalStateException.class, () -> budget.expirar(EXPIRA.minusSeconds(1)));
        assertEquals(EstadoOrcamento.EXPIRADO, budget.expirar(EXPIRA).estado());
        assertEquals(EstadoOrcamento.CANCELADO, budget.cancelar().estado());
        assertEquals(EstadoOrcamento.CANCELADO, budget.aprovar(CRIADO).cancelar().estado());
        assertThrows(IllegalStateException.class, () -> budget.recusar().cancelar());
    }

    @Test void rejeitaItensDuplicadosOuInvalidos() {
        var first = item();
        assertThrows(IllegalArgumentException.class, () -> Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(), CRIADO, EXPIRA));
        assertThrows(IllegalArgumentException.class, () -> Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(first, first), CRIADO, EXPIRA));
        assertThrows(IllegalArgumentException.class, () -> new ItemPeca(UUID.randomUUID(), 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new ItemPeca(UUID.randomUUID(), 1, BigDecimal.valueOf(-1)));
        assertThrows(IllegalArgumentException.class, () -> new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(),
                " ", BigDecimal.ONE, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(),
                "Servico", BigDecimal.ZERO, List.of()));
        var part = new ItemPeca(UUID.randomUUID(), 1, BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(),
                "Servico", BigDecimal.ONE, List.of(part, part)));
    }

    @Test void rejeitaTotalAlteradoEExpiracaoInvalida() {
        var budget = orcamento();
        assertThrows(IllegalArgumentException.class, () -> new Orcamento(budget.id(), budget.osId(),
                budget.clienteId(), budget.itens(), BigDecimal.ONE, budget.estado(), CRIADO, EXPIRA));
        assertThrows(IllegalArgumentException.class, () -> Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(item()), CRIADO, CRIADO));
    }
}
