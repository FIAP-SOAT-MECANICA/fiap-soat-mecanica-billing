package br.com.fiap.soat.mecanica.billing.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PagamentoTest {
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    private static Orcamento aprovado() {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Revisao",
                new BigDecimal("50.00"), List.of());
        return Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(item), NOW, NOW.plusSeconds(3600)).aprovar(NOW);
    }

    @Test void criaSomenteAposAprovacaoEConfirmaUmaVez() {
        var budget = aprovado();
        var payment = Pagamento.criar(UUID.randomUUID(), budget, "mp-123", NOW);
        assertEquals(budget.valorTotal(), payment.valor());
        assertEquals(budget.osId(), payment.osId());
        assertThrows(IllegalStateException.class, () -> Pagamento.criar(UUID.randomUUID(),
                budget.cancelar(), "mp-124", NOW));
        var confirmed = payment.confirmar(NOW.plusSeconds(10));
        assertSame(confirmed, confirmed.confirmar(NOW.plusSeconds(20)));
        assertThrows(IllegalStateException.class, () -> confirmed.recusar(NOW.plusSeconds(20)));
    }

    @Test void recusaNaoPodeVirarAprovacaoMasNovaTentativaPode() {
        var budget = aprovado();
        var refused = Pagamento.criar(UUID.randomUUID(), budget, "mp-1", NOW).recusar(NOW.plusSeconds(5));
        assertEquals(EstadoPagamento.RECUSADO, refused.estado());
        assertThrows(IllegalStateException.class, () -> refused.confirmar(NOW.plusSeconds(6)));
        var retry = Pagamento.criar(UUID.randomUUID(), budget, "mp-2", NOW.plusSeconds(7));
        assertNotEquals(refused.id(), retry.id());
        assertEquals(EstadoPagamento.APROVADO, retry.confirmar(NOW.plusSeconds(8)).estado());
    }

    @Test void rejeitaValorReferenciaEInstantesInvalidos() {
        UUID id = UUID.randomUUID();
        UUID budgetId = UUID.randomUUID();
        UUID osId = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> new Pagamento(id, budgetId, osId,
                BigDecimal.ZERO, "mp-1", EstadoPagamento.PENDENTE, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new Pagamento(id, budgetId, osId,
                BigDecimal.ONE, " ", EstadoPagamento.PENDENTE, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new Pagamento(id, budgetId, osId,
                BigDecimal.ONE, "mp-1", EstadoPagamento.APROVADO, NOW, null));
        assertThrows(IllegalArgumentException.class, () -> new Pagamento(id, budgetId, osId,
                BigDecimal.ONE, "mp-1", EstadoPagamento.RECUSADO, NOW, NOW.minusSeconds(1)));
    }
}
