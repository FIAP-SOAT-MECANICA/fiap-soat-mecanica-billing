package br.com.fiap.soat.mecanica.billing.application.orcamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.application.orcamento.CancelarOrcamentoUseCase.Comando;
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

class CancelarOrcamentoUseCaseTest {
    static final Instant AGORA = Instant.parse("2026-11-17T12:00:00Z");

    MensagemProcessadaRepository inbox = mock(MensagemProcessadaRepository.class);
    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    CancelarOrcamentoUseCase useCase = new CancelarOrcamentoUseCase(inbox, orcamentos,
            Clock.fixed(AGORA, ZoneOffset.UTC));
    UUID messageId = UUID.randomUUID(), osId = UUID.randomUUID();

    @BeforeEach void inboxAceita() { when(inbox.registrar(any(), any())).thenReturn(true); }

    Registro registro(EstadoOrcamento estado, UUID os) {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Servico", new BigDecimal("10.00"), List.of());
        var o = new Orcamento(UUID.randomUUID(), os, UUID.randomUUID(), List.of(item), new BigDecimal("10.00"), estado,
                AGORA.minusSeconds(60), AGORA.plusSeconds(3600));
        return new Registro(o, 1, 4);
    }

    @Test void cancelaOrcamentoGeradoLidoNaVersaoCorreta() {
        Registro r = registro(EstadoOrcamento.GERADO, osId);
        when(orcamentos.buscarPorId(r.orcamento().id())).thenReturn(Optional.of(r));

        assertEquals(ResultadoComando.PROCESSADO,
                useCase.executar(new Comando(messageId, osId, r.orcamento().id())));

        ArgumentCaptor<Orcamento> gravado = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos).atualizar(gravado.capture(), eq(4L));
        assertEquals(EstadoOrcamento.CANCELADO, gravado.getValue().estado());
    }

    @Test void cancelaOrcamentoAprovadoPoisEhOCasoDeFaltaDeEstoque() {
        Registro r = registro(EstadoOrcamento.APROVADO, osId);
        when(orcamentos.buscarPorId(r.orcamento().id())).thenReturn(Optional.of(r));

        useCase.executar(new Comando(messageId, osId, r.orcamento().id()));

        verify(orcamentos).atualizar(any(), eq(4L));
    }

    @Test void semIdUsaOUltimoOrcamentoDaOs() {
        Registro r = registro(EstadoOrcamento.GERADO, osId);
        when(orcamentos.buscarUltimoPorOs(osId)).thenReturn(Optional.of(r));

        useCase.executar(new Comando(messageId, osId, null));

        verify(orcamentos).atualizar(any(), anyLong());
    }

    @Test void mensagemRepetidaNaoFazNada() {
        when(inbox.registrar(messageId, AGORA)).thenReturn(false);
        assertEquals(ResultadoComando.DUPLICADO, useCase.executar(new Comando(messageId, osId, UUID.randomUUID())));
        verifyNoInteractions(orcamentos);
    }

    @Test void orcamentoInexistenteEhConfirmadoSemErro() {
        when(orcamentos.buscarPorId(any())).thenReturn(Optional.empty());
        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(messageId, osId, UUID.randomUUID())));
        verify(orcamentos, never()).atualizar(any(), anyLong());
    }

    @Test void orcamentoDeOutraOsNaoEhTocado() {
        Registro r = registro(EstadoOrcamento.GERADO, UUID.randomUUID());
        when(orcamentos.buscarPorId(r.orcamento().id())).thenReturn(Optional.of(r));

        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(messageId, osId, r.orcamento().id())));

        verify(orcamentos, never()).atualizar(any(), anyLong());
    }

    @Test void orcamentoJaEncerradoFicaComoEsta() {
        for (EstadoOrcamento estado : List.of(EstadoOrcamento.RECUSADO, EstadoOrcamento.EXPIRADO, EstadoOrcamento.CANCELADO)) {
            Registro r = registro(estado, osId);
            when(orcamentos.buscarPorId(r.orcamento().id())).thenReturn(Optional.of(r));
            assertEquals(ResultadoComando.PROCESSADO, useCase.executar(new Comando(UUID.randomUUID(), osId, r.orcamento().id())));
        }
        verify(orcamentos, never()).atualizar(any(), anyLong());
    }
}
