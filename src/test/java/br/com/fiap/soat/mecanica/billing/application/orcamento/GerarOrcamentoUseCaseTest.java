package br.com.fiap.soat.mecanica.billing.application.orcamento;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.application.orcamento.GerarOrcamentoUseCase.Comando;
import br.com.fiap.soat.mecanica.billing.application.orcamento.GerarOrcamentoUseCase.Item;
import br.com.fiap.soat.mecanica.billing.application.orcamento.GerarOrcamentoUseCase.Peca;
import br.com.fiap.soat.mecanica.billing.domain.*;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GerarOrcamentoUseCaseTest {
    static final Instant AGORA = Instant.parse("2026-11-17T12:00:00Z");

    MensagemProcessadaRepository inbox = mock(MensagemProcessadaRepository.class);
    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    EventosOrcamento eventos = mock(EventosOrcamento.class);
    GerarOrcamentoUseCase useCase = new GerarOrcamentoUseCase(inbox, orcamentos, eventos,
            Clock.fixed(AGORA, ZoneOffset.UTC), 7);

    UUID messageId = UUID.randomUUID(), sagaId = UUID.randomUUID(), osId = UUID.randomUUID(),
            clienteId = UUID.randomUUID();

    @BeforeEach void inboxAceitaPorPadrao() {
        when(inbox.registrar(any(), any())).thenReturn(true);
        when(orcamentos.buscarUltimoPorOs(osId)).thenReturn(Optional.empty());
        when(orcamentos.inserir(any())).thenAnswer(i -> new Registro(i.getArgument(0), 1, 0));
    }

    Comando comando(BigDecimal precoMdo, BigDecimal valorPeca) {
        var peca = new Peca(UUID.randomUUID(), 2, valorPeca);
        return new Comando(messageId, sagaId, "corr-1", osId, clienteId,
                List.of(new Item(UUID.randomUUID(), "Troca de oleo", precoMdo, List.of(peca))));
    }

    Orcamento existente(EstadoOrcamento estado) {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Servico", new BigDecimal("10.00"), List.of());
        return new Orcamento(UUID.randomUUID(), osId, clienteId, List.of(item), new BigDecimal("10.00"), estado,
                AGORA.minusSeconds(60), AGORA.plus(Duration.ofDays(1)));
    }

    @Test void geraOrcamentoComValidadeEEmiteOEventoComSagaECausalidade() {
        assertEquals(ResultadoComando.PROCESSADO,
                useCase.executar(comando(new BigDecimal("150.5"), new BigDecimal("20"))));

        ArgumentCaptor<Orcamento> gravado = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos).inserir(gravado.capture());
        Orcamento o = gravado.getValue();
        assertEquals(EstadoOrcamento.GERADO, o.estado());
        assertEquals(osId, o.osId());
        assertEquals(clienteId, o.clienteId());
        assertEquals(new BigDecimal("190.50"), o.valorTotal());
        assertEquals(AGORA, o.criadoEm());
        assertEquals(AGORA.plus(Duration.ofDays(7)), o.expiraEm());
        verify(eventos).gerado(o, sagaId, messageId, "corr-1", AGORA);
    }

    @Test void mensagemRepetidaNaoFazNada() {
        when(inbox.registrar(messageId, AGORA)).thenReturn(false);
        assertEquals(ResultadoComando.DUPLICADO, useCase.executar(comando(BigDecimal.TEN, BigDecimal.ONE)));
        verifyNoInteractions(orcamentos, eventos);
    }

    @Test void osComOrcamentoGeradoNaoCriaOutroMasReenviaOEvento() {
        Orcamento vigente = existente(EstadoOrcamento.GERADO);
        when(orcamentos.buscarUltimoPorOs(osId)).thenReturn(Optional.of(new Registro(vigente, 1, 0)));

        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(comando(BigDecimal.TEN, BigDecimal.ONE)));

        verify(orcamentos, never()).inserir(any());
        verify(eventos).gerado(vigente, sagaId, messageId, "corr-1", AGORA);
    }

    @Test void osComOrcamentoAprovadoNaoCriaOutroNemAnunciaGerado() {
        when(orcamentos.buscarUltimoPorOs(osId))
                .thenReturn(Optional.of(new Registro(existente(EstadoOrcamento.APROVADO), 1, 0)));

        assertEquals(ResultadoComando.PROCESSADO, useCase.executar(comando(BigDecimal.TEN, BigDecimal.ONE)));

        verify(orcamentos, never()).inserir(any());
        verifyNoInteractions(eventos);
    }

    @Test void orcamentoAnteriorRecusadoPermiteNovaRevisao() {
        when(orcamentos.buscarUltimoPorOs(osId))
                .thenReturn(Optional.of(new Registro(existente(EstadoOrcamento.RECUSADO), 1, 1)));

        useCase.executar(comando(BigDecimal.TEN, BigDecimal.ONE));

        verify(orcamentos).inserir(any());
    }

    @Test void valorComMaisDeDuasCasasEhRejeitadoEnaoArredondado() {
        var erro = assertThrows(IllegalArgumentException.class,
                () -> useCase.executar(comando(new BigDecimal("10.123"), BigDecimal.ONE)));
        assertTrue(erro.getMessage().contains("duas casas"));
        verify(orcamentos, never()).inserir(any());
    }

    @Test void itensAusentesSaoRejeitados() {
        var semItens = new Comando(messageId, sagaId, null, osId, clienteId, null);
        assertThrows(IllegalArgumentException.class, () -> useCase.executar(semItens));
    }

    @Test void itemSemValorEhRejeitadoPeloDominio() {
        var vazio = new Comando(messageId, sagaId, null, osId, clienteId,
                List.of(new Item(UUID.randomUUID(), "Servico", BigDecimal.ZERO, null)));
        assertThrows(IllegalArgumentException.class, () -> useCase.executar(vazio));
    }
}
