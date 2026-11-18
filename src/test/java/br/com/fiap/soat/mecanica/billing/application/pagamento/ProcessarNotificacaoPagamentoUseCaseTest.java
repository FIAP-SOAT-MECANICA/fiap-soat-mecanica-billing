package br.com.fiap.soat.mecanica.billing.application.pagamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.application.pagamento.ProcessarNotificacaoPagamentoUseCase.Notificacao;
import br.com.fiap.soat.mecanica.billing.application.pagamento.ProcessarNotificacaoPagamentoUseCase.Resultado;
import br.com.fiap.soat.mecanica.billing.domain.*;
import br.com.fiap.soat.mecanica.billing.domain.CobrancaGateway.PagamentoProvedor;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessarNotificacaoPagamentoUseCaseTest {
    static final Instant AGORA = Instant.parse("2026-11-17T12:00:00Z");
    static final Notificacao NOTIFICACAO = new Notificacao("evt-1", "9001", "req-1");

    WebhookEventoRepository webhooks = mock(WebhookEventoRepository.class);
    PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    CobrancaGateway gateway = mock(CobrancaGateway.class);
    EventosPagamento eventos = mock(EventosPagamento.class);
    ProcessarNotificacaoPagamentoUseCase useCase = new ProcessarNotificacaoPagamentoUseCase(webhooks, pagamentos,
            gateway, eventos, Clock.fixed(AGORA, ZoneOffset.UTC));

    @BeforeEach void recebimentoNovo() { when(webhooks.registrarRecebimento(any(), any(), any())).thenReturn(true); }

    Registro cobranca(EstadoPagamento estado) {
        Instant decidido = estado == EstadoPagamento.PENDENTE ? null : AGORA;
        var p = new Pagamento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, "ref-1", estado,
                AGORA.minusSeconds(60), decidido);
        var registro = new Registro(p, "https://pagar", 7);
        when(pagamentos.buscarPorReferenciaExterna("ref-1")).thenReturn(Optional.of(registro));
        return registro;
    }

    void provedorDiz(EstadoPagamento estado) {
        when(gateway.consultarPagamento("9001")).thenReturn(Optional.of(new PagamentoProvedor("ref-1", estado)));
    }

    @Test void aprovacaoConfirmaACobrancaEAnunciaPagamentoConfirmado() {
        cobranca(EstadoPagamento.PENDENTE);
        provedorDiz(EstadoPagamento.APROVADO);

        assertEquals(Resultado.DECIDIDO, useCase.executar(NOTIFICACAO));

        ArgumentCaptor<Pagamento> gravado = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentos).atualizar(gravado.capture(), eq(7L));
        assertEquals(EstadoPagamento.APROVADO, gravado.getValue().estado());
        verify(eventos).confirmado(gravado.getValue(), "req-1", AGORA);
        verify(webhooks).marcarProcessado("evt-1", AGORA);
    }

    @Test void recusaRecusaACobrancaEAnunciaPagamentoRecusado() {
        cobranca(EstadoPagamento.PENDENTE);
        provedorDiz(EstadoPagamento.RECUSADO);

        assertEquals(Resultado.DECIDIDO, useCase.executar(NOTIFICACAO));

        ArgumentCaptor<Pagamento> gravado = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentos).atualizar(gravado.capture(), eq(7L));
        assertEquals(EstadoPagamento.RECUSADO, gravado.getValue().estado());
        verify(eventos).recusado(gravado.getValue(), "req-1", AGORA);
        verify(eventos, never()).confirmado(any(), any(), any());
    }

    @Test void notificacaoJaProcessadaNaoConsultaNemMuda() {
        when(webhooks.registrarRecebimento("evt-1", "9001", AGORA)).thenReturn(false);
        assertEquals(Resultado.JA_PROCESSADA, useCase.executar(NOTIFICACAO));
        verifyNoInteractions(gateway, pagamentos, eventos);
        verify(webhooks, never()).marcarProcessado(any(), any());
    }

    @Test void pagamentoAindaEmAndamentoNaoDecideNada() {
        cobranca(EstadoPagamento.PENDENTE);
        provedorDiz(EstadoPagamento.PENDENTE);
        assertEquals(Resultado.SEM_EFEITO, useCase.executar(NOTIFICACAO));
        verify(pagamentos, never()).atualizar(any(), anyLong());
        verifyNoInteractions(eventos);
        verify(webhooks).marcarProcessado("evt-1", AGORA);
    }

    @Test void pagamentoDesconhecidoOuDeOutraIntegracaoEhIgnorado() {
        when(gateway.consultarPagamento("9001")).thenReturn(Optional.empty());
        assertEquals(Resultado.SEM_EFEITO, useCase.executar(NOTIFICACAO));

        when(gateway.consultarPagamento("9001")).thenReturn(Optional.of(new PagamentoProvedor(null, EstadoPagamento.APROVADO)));
        assertEquals(Resultado.SEM_EFEITO, useCase.executar(NOTIFICACAO));

        when(gateway.consultarPagamento("9001")).thenReturn(Optional.of(new PagamentoProvedor("ref-1", EstadoPagamento.APROVADO)));
        when(pagamentos.buscarPorReferenciaExterna("ref-1")).thenReturn(Optional.empty());
        assertEquals(Resultado.SEM_EFEITO, useCase.executar(NOTIFICACAO));

        verifyNoInteractions(eventos);
        verify(pagamentos, never()).atualizar(any(), anyLong());
        verify(webhooks, times(3)).marcarProcessado("evt-1", AGORA);
    }

    @Test void notificacaoRepetidaDaMesmaDecisaoNaoRepeteOEvento() {
        cobranca(EstadoPagamento.APROVADO);
        provedorDiz(EstadoPagamento.APROVADO);
        assertEquals(Resultado.SEM_EFEITO, useCase.executar(NOTIFICACAO));
        verifyNoInteractions(eventos);
        verify(pagamentos, never()).atualizar(any(), anyLong());
    }

    @Test void decisaoTardiaSobreCobrancaJaEncerradaNaoMudaNada() {
        for (EstadoPagamento atual : new EstadoPagamento[] {EstadoPagamento.CANCELADO, EstadoPagamento.RECUSADO}) {
            cobranca(atual);
            provedorDiz(EstadoPagamento.APROVADO);
            assertEquals(Resultado.SEM_EFEITO, useCase.executar(NOTIFICACAO));
        }
        cobranca(EstadoPagamento.APROVADO);
        provedorDiz(EstadoPagamento.RECUSADO);
        assertEquals(Resultado.SEM_EFEITO, useCase.executar(NOTIFICACAO));
        verifyNoInteractions(eventos);
        verify(pagamentos, never()).atualizar(any(), anyLong());
    }

    @Test void falhaDoProvedorSobeParaONotificadorTentarDeNovo() {
        when(gateway.consultarPagamento("9001")).thenThrow(new FalhaCobrancaException("fora do ar"));
        assertThrows(FalhaCobrancaException.class, () -> useCase.executar(NOTIFICACAO));
        verify(webhooks, never()).marcarProcessado(any(), any());
    }
}
