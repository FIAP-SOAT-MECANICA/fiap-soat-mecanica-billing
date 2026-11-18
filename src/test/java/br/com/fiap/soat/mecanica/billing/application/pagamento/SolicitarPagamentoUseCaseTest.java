package br.com.fiap.soat.mecanica.billing.application.pagamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.application.pagamento.SolicitarPagamentoUseCase.Comando;
import br.com.fiap.soat.mecanica.billing.domain.*;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SolicitarPagamentoUseCaseTest {
    static final Instant AGORA = Instant.parse("2026-11-17T12:00:00Z");

    MensagemProcessadaRepository inbox = mock(MensagemProcessadaRepository.class);
    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    CobrancaGateway gateway = mock(CobrancaGateway.class);
    SolicitarPagamentoUseCase useCase = new SolicitarPagamentoUseCase(inbox, orcamentos, pagamentos, gateway,
            Clock.fixed(AGORA, ZoneOffset.UTC));
    UUID messageId = UUID.randomUUID(), osId = UUID.randomUUID();

    @BeforeEach void padroes() {
        when(inbox.registrar(any(), any())).thenReturn(true);
        when(pagamentos.listarPorOs(osId)).thenReturn(List.of());
        when(gateway.criar(anyString(), any(), any(), any()))
                .thenAnswer(i -> new CobrancaGateway.Cobranca(i.getArgument(0), "https://pagar/abc"));
    }

    Orcamento orcamento(EstadoOrcamento estado, UUID os) {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Servico", new BigDecimal("190.50"), List.of());
        return new Orcamento(UUID.randomUUID(), os, UUID.randomUUID(), List.of(item), new BigDecimal("190.50"), estado,
                AGORA.minusSeconds(3600), AGORA.plusSeconds(3600));
    }

    Orcamento aprovadoNaFila() {
        Orcamento o = orcamento(EstadoOrcamento.APROVADO, osId);
        when(orcamentos.buscarPorId(o.id())).thenReturn(Optional.of(new Registro(o, 1, 2)));
        return o;
    }

    PagamentoRepository.Registro cobranca(Orcamento o, EstadoPagamento estado) {
        Instant decidido = estado == EstadoPagamento.PENDENTE ? null : AGORA;
        return new PagamentoRepository.Registro(new Pagamento(UUID.randomUUID(), o.id(), o.osId(), o.valorTotal(),
                "ref-" + estado, estado, AGORA.minusSeconds(60), decidido), "https://pagar/antiga", 0);
    }

    @Test void abreOCheckoutDoTotalEGravaACobrancaPendenteComOLink() {
        Orcamento o = aprovadoNaFila();

        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(messageId, osId, o.id())));

        ArgumentCaptor<String> referencia = ArgumentCaptor.forClass(String.class);
        verify(gateway).criar(referencia.capture(), eq(osId), eq(o.id()), eq(new BigDecimal("190.50")));
        ArgumentCaptor<Pagamento> gravado = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentos).inserir(gravado.capture(), eq("https://pagar/abc"));
        Pagamento p = gravado.getValue();
        assertEquals(EstadoPagamento.PENDENTE, p.estado());
        assertEquals(referencia.getValue(), p.referenciaExterna());
        assertEquals(p.id().toString(), p.referenciaExterna());
        assertEquals(o.id(), p.orcamentoId());
        assertEquals(AGORA, p.criadoEm());
    }

    @Test void aMesmaMensagemSempreGeraAMesmaReferencia() {
        Orcamento o = aprovadoNaFila();
        useCase.executar(new Comando(messageId, osId, o.id()));
        useCase.executar(new Comando(messageId, osId, o.id()));
        ArgumentCaptor<String> referencia = ArgumentCaptor.forClass(String.class);
        verify(gateway, times(2)).criar(referencia.capture(), any(), any(), any());
        assertEquals(referencia.getAllValues().get(0), referencia.getAllValues().get(1));
    }

    @Test void semOrcamentoIdUsaOMaisRecenteDaOs() {
        Orcamento o = orcamento(EstadoOrcamento.APROVADO, osId);
        when(orcamentos.buscarUltimoPorOs(osId)).thenReturn(Optional.of(new Registro(o, 1, 0)));
        useCase.executar(new Comando(messageId, osId, null));
        verify(pagamentos).inserir(any(), anyString());
    }

    @Test void mensagemRepetidaNaoFazNada() {
        when(inbox.registrar(messageId, AGORA)).thenReturn(false);
        assertEquals(ResultadoComando.DUPLICADO, useCase.executar(new Comando(messageId, osId, UUID.randomUUID())));
        verifyNoInteractions(orcamentos, pagamentos, gateway);
    }

    @Test void cobrancaPendenteOuPagaNaoGeraOutra() {
        for (EstadoPagamento estado : List.of(EstadoPagamento.PENDENTE, EstadoPagamento.APROVADO)) {
            Orcamento o = aprovadoNaFila();
            when(pagamentos.listarPorOs(osId)).thenReturn(List.of(cobranca(o, estado)));
            assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(UUID.randomUUID(), osId, o.id())));
        }
        verifyNoInteractions(gateway);
        verify(pagamentos, never()).inserir(any(), any());
    }

    @Test void cobrancaRecusadaOuCanceladaPermiteNovaTentativa() {
        Orcamento o = aprovadoNaFila();
        when(pagamentos.listarPorOs(osId)).thenReturn(
                List.of(cobranca(o, EstadoPagamento.RECUSADO), cobranca(o, EstadoPagamento.CANCELADO)));
        useCase.executar(new Comando(messageId, osId, o.id()));
        verify(pagamentos).inserir(any(), anyString());
    }

    @Test void cobrancaDeOutroOrcamentoDaOsNaoImpede() {
        Orcamento o = aprovadoNaFila();
        Orcamento antigo = orcamento(EstadoOrcamento.CANCELADO, osId);
        when(pagamentos.listarPorOs(osId)).thenReturn(List.of(cobranca(antigo, EstadoPagamento.PENDENTE)));
        useCase.executar(new Comando(messageId, osId, o.id()));
        verify(pagamentos).inserir(any(), anyString());
    }

    @Test void orcamentoInexistenteDeOutraOsOuNaoAprovadoFalha() {
        when(orcamentos.buscarPorId(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> useCase.executar(new Comando(messageId, osId, UUID.randomUUID())));

        Orcamento alheio = orcamento(EstadoOrcamento.APROVADO, UUID.randomUUID());
        when(orcamentos.buscarPorId(alheio.id())).thenReturn(Optional.of(new Registro(alheio, 1, 0)));
        assertThrows(IllegalStateException.class, () -> useCase.executar(new Comando(messageId, osId, alheio.id())));

        for (EstadoOrcamento estado : List.of(EstadoOrcamento.GERADO, EstadoOrcamento.RECUSADO,
                EstadoOrcamento.EXPIRADO, EstadoOrcamento.CANCELADO)) {
            Orcamento o = orcamento(estado, osId);
            when(orcamentos.buscarPorId(o.id())).thenReturn(Optional.of(new Registro(o, 1, 0)));
            assertThrows(IllegalStateException.class, () -> useCase.executar(new Comando(messageId, osId, o.id())));
        }
        verifyNoInteractions(gateway);
    }

    @Test void falhaDoProvedorSobeParaAMensagemVoltarParaAFila() {
        Orcamento o = aprovadoNaFila();
        when(gateway.criar(anyString(), any(), any(), any())).thenThrow(new FalhaCobrancaException("fora do ar"));
        assertThrows(FalhaCobrancaException.class, () -> useCase.executar(new Comando(messageId, osId, o.id())));
        verify(pagamentos, never()).inserir(any(), any());
    }
}
