package br.com.fiap.soat.mecanica.billing.application.orcamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.domain.*;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ExpirarOrcamentosUseCaseTest {
    static final Instant AGORA = Instant.parse("2026-11-17T12:00:00Z");

    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    EventosOrcamento eventos = mock(EventosOrcamento.class);
    ExpirarOrcamentosUseCase useCase = new ExpirarOrcamentosUseCase(orcamentos, eventos,
            Clock.fixed(AGORA, ZoneOffset.UTC));

    Registro vencido(long versao) {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Servico", new BigDecimal("10.00"), List.of());
        var o = new Orcamento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(item), new BigDecimal("10.00"),
                EstadoOrcamento.GERADO, AGORA.minusSeconds(7200), AGORA.minusSeconds(60));
        return new Registro(o, 1, versao);
    }

    @Test void expiraCadaVencidoNaVersaoLidaEAnunciaComoRecusado() {
        Registro a = vencido(2), b = vencido(5);
        when(orcamentos.buscarGeradosVencidos(AGORA, 50)).thenReturn(List.of(a, b));
        when(orcamentos.atualizar(any(), anyLong())).thenAnswer(i -> new Registro(i.getArgument(0), 1, 0));

        assertEquals(2, useCase.executar(50));

        ArgumentCaptor<Orcamento> gravados = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos).atualizar(gravados.capture(), eq(2L));
        verify(orcamentos).atualizar(any(), eq(5L));
        assertEquals(EstadoOrcamento.EXPIRADO, gravados.getValue().estado());
        assertEquals(a.orcamento().id(), gravados.getValue().id());
        verify(eventos).recusado(gravados.getValue(), MotivoRecusa.EXPIRADO, null, AGORA);
        verify(eventos, times(2)).recusado(any(), eq(MotivoRecusa.EXPIRADO), eq(null), eq(AGORA));
    }

    @Test void semVencidosNaoFazNada() {
        when(orcamentos.buscarGeradosVencidos(AGORA, 10)).thenReturn(List.of());
        assertEquals(0, useCase.executar(10));
        verify(orcamentos, never()).atualizar(any(), anyLong());
        verifyNoInteractions(eventos);
    }

    @Test void conflitoDeVersaoSobeParaODesfazerOLote() {
        when(orcamentos.buscarGeradosVencidos(AGORA, 50)).thenReturn(List.of(vencido(1)));
        when(orcamentos.atualizar(any(), anyLong())).thenThrow(new ConflitoDeVersaoException("aprovado em paralelo"));
        assertThrows(ConflitoDeVersaoException.class, () -> useCase.executar(50));
        verifyNoInteractions(eventos);
    }
}
