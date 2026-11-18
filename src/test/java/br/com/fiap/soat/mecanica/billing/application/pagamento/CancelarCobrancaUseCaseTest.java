package br.com.fiap.soat.mecanica.billing.application.pagamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.application.pagamento.CancelarCobrancaUseCase.Comando;
import br.com.fiap.soat.mecanica.billing.domain.*;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CancelarCobrancaUseCaseTest {
    static final Instant AGORA = Instant.parse("2026-11-17T12:00:00Z");

    MensagemProcessadaRepository inbox = mock(MensagemProcessadaRepository.class);
    PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    CancelarCobrancaUseCase useCase = new CancelarCobrancaUseCase(inbox, pagamentos, Clock.fixed(AGORA, ZoneOffset.UTC));
    UUID messageId = UUID.randomUUID(), osId = UUID.randomUUID(), orcamentoId = UUID.randomUUID();

    @BeforeEach void inboxAceita() { when(inbox.registrar(any(), any())).thenReturn(true); }

    Registro registro(UUID orcamento, EstadoPagamento estado, long versao) {
        Instant decidido = estado == EstadoPagamento.PENDENTE ? null : AGORA;
        return new Registro(new Pagamento(UUID.randomUUID(), orcamento, osId, BigDecimal.TEN, "ref-" + UUID.randomUUID(),
                estado, AGORA.minusSeconds(60), decidido), null, versao);
    }

    @Test void cancelaACobrancaPendenteNaVersaoLida() {
        Registro pendente = registro(orcamentoId, EstadoPagamento.PENDENTE, 3);
        when(pagamentos.listarPorOs(osId)).thenReturn(List.of(pendente));

        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(messageId, osId, orcamentoId)));

        ArgumentCaptor<Pagamento> gravado = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentos).atualizar(gravado.capture(), eq(3L));
        assertEquals(EstadoPagamento.CANCELADO, gravado.getValue().estado());
        assertEquals(AGORA, gravado.getValue().decididoEm());
    }

    @Test void semOrcamentoIdCancelaTodasAsPendentesDaOs() {
        when(pagamentos.listarPorOs(osId)).thenReturn(List.of(
                registro(UUID.randomUUID(), EstadoPagamento.PENDENTE, 0), registro(orcamentoId, EstadoPagamento.PENDENTE, 0)));
        useCase.executar(new Comando(messageId, osId, null));
        verify(pagamentos, times(2)).atualizar(any(), anyLong());
    }

    @Test void cobrancaDeOutroOrcamentoNaoEhTocada() {
        when(pagamentos.listarPorOs(osId)).thenReturn(List.of(registro(UUID.randomUUID(), EstadoPagamento.PENDENTE, 0)));
        useCase.executar(new Comando(messageId, osId, orcamentoId));
        verify(pagamentos, never()).atualizar(any(), anyLong());
    }

    @Test void cobrancaJaDecididaOuInexistenteEhConfirmadaSemErro() {
        when(pagamentos.listarPorOs(osId)).thenReturn(List.of(registro(orcamentoId, EstadoPagamento.RECUSADO, 1),
                registro(orcamentoId, EstadoPagamento.APROVADO, 1), registro(orcamentoId, EstadoPagamento.CANCELADO, 1)));
        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(messageId, osId, orcamentoId)));

        when(pagamentos.listarPorOs(osId)).thenReturn(List.of());
        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(UUID.randomUUID(), osId, orcamentoId)));
        verify(pagamentos, never()).atualizar(any(), anyLong());
    }

    @Test void mensagemRepetidaNaoFazNada() {
        when(inbox.registrar(messageId, AGORA)).thenReturn(false);
        assertEquals(ResultadoComando.DUPLICADO, useCase.executar(new Comando(messageId, osId, orcamentoId)));
        verifyNoInteractions(pagamentos);
    }
}
