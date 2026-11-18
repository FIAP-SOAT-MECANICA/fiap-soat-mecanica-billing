package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.application.orcamento.CancelarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.GerarOrcamentoUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TratadorComandosTest {
    GerarOrcamentoUseCase gerar = mock(GerarOrcamentoUseCase.class);
    CancelarOrcamentoUseCase cancelar = mock(CancelarOrcamentoUseCase.class);
    TratadorComandos tratador = new TratadorComandos(new ObjectMapper().findAndRegisterModules(), gerar, cancelar);

    UUID messageId = UUID.randomUUID(), sagaId = UUID.randomUUID(), osId = UUID.randomUUID(),
            clienteId = UUID.randomUUID(), servicoId = UUID.randomUUID(), pecaId = UUID.randomUUID(),
            orcamentoId = UUID.randomUUID();

    String envelope(String type, int version, String payload) {
        return """
                {"messageId":"%s","type":"%s","version":%d,"sagaId":"%s","osId":"%s","correlationId":"corr-9",
                 "causationId":null,"occurredAt":"2026-11-17T12:00:00Z","payload":%s}"""
                .formatted(messageId, type, version, sagaId, osId, payload);
    }

    String gerarPayload(String osNoPayload) {
        return """
                {"osId":"%s","clienteId":"%s","itens":[{"servicoId":"%s","nomeServico":"Troca de oleo",
                 "precoMdo":150.5,"pecas":[{"pecaId":"%s","qtd":2,"valorUnit":20.00}]}]}"""
                .formatted(osNoPayload, clienteId, servicoId, pecaId);
    }

    @Test void gerarOrcamentoTraduzOsNomesDoContratoParaOCaso() {
        assertTrue(tratador.tratar(envelope("GerarOrcamento", 1, gerarPayload(osId.toString()))));

        var comando = ArgumentCaptor.forClass(GerarOrcamentoUseCase.Comando.class);
        verify(gerar).executar(comando.capture());
        var c = comando.getValue();
        assertEquals(messageId, c.messageId());
        assertEquals(sagaId, c.sagaId());
        assertEquals("corr-9", c.correlationId());
        assertEquals(osId, c.osId());
        assertEquals(clienteId, c.clienteId());
        var item = c.itens().get(0);
        assertEquals(servicoId, item.servicoId());
        assertEquals("Troca de oleo", item.nomeServico());
        assertEquals(0, new BigDecimal("150.5").compareTo(item.precoMdo()));
        assertEquals(pecaId, item.pecas().get(0).pecaId());
        assertEquals(2, item.pecas().get(0).quantidade());
        assertEquals(0, new BigDecimal("20").compareTo(item.pecas().get(0).valorUnitario()));
    }

    @Test void itemSemPecasViraListaVazia() {
        String payload = """
                {"osId":"%s","clienteId":"%s","itens":[{"servicoId":"%s","nomeServico":"Revisao","precoMdo":80}]}"""
                .formatted(osId, clienteId, servicoId);
        assertTrue(tratador.tratar(envelope("GerarOrcamento", 1, payload)));

        var comando = ArgumentCaptor.forClass(GerarOrcamentoUseCase.Comando.class);
        verify(gerar).executar(comando.capture());
        assertTrue(comando.getValue().itens().get(0).pecas().isEmpty());
    }

    @Test void cancelarOrcamentoEncaminhaAReferencia() {
        assertTrue(tratador.tratar(envelope("CancelarOrcamento", 1,
                "{\"osId\":\"%s\",\"orcamentoId\":\"%s\"}".formatted(osId, orcamentoId))));
        verify(cancelar).executar(new CancelarOrcamentoUseCase.Comando(messageId, osId, orcamentoId));
    }

    @Test void cancelarSemOrcamentoIdEhAceito() {
        assertTrue(tratador.tratar(envelope("CancelarOrcamento", 1, "{\"osId\":\"%s\"}".formatted(osId))));
        verify(cancelar).executar(new CancelarOrcamentoUseCase.Comando(messageId, osId, null));
    }

    @Test void tipoSemTratadorFicaNaFila() {
        // SolicitarPagamento and CancelarCobranca wait for the Mercado Pago integration.
        assertFalse(tratador.tratar(envelope("SolicitarPagamento", 1,
                "{\"osId\":\"%s\",\"orcamentoId\":\"%s\"}".formatted(osId, orcamentoId))));
        verifyNoInteractions(gerar, cancelar);
    }

    @Test void payloadComOsDiferenteDoEnvelopeEhRejeitado() {
        assertThrows(MensagemInvalidaException.class,
                () -> tratador.tratar(envelope("GerarOrcamento", 1, gerarPayload(UUID.randomUUID().toString()))));
        verifyNoInteractions(gerar, cancelar);
    }

    @Test void versaoNaoSuportadaEhRejeitada() {
        assertThrows(MensagemInvalidaException.class,
                () -> tratador.tratar(envelope("GerarOrcamento", 2, gerarPayload(osId.toString()))));
    }

    @Test void camposObrigatoriosAusentesSaoRejeitados() {
        assertThrows(MensagemInvalidaException.class, () -> tratador.tratar(
                "{\"type\":\"GerarOrcamento\",\"version\":1,\"osId\":\"%s\",\"payload\":{\"osId\":\"%s\"}}"
                        .formatted(osId, osId)));
        assertThrows(MensagemInvalidaException.class, () -> tratador.tratar(
                "{\"messageId\":\"%s\",\"version\":1,\"payload\":{}}".formatted(messageId)));
        assertThrows(MensagemInvalidaException.class, () -> tratador.tratar(
                "{\"messageId\":\"%s\",\"type\":\" \",\"version\":1,\"osId\":\"%s\",\"payload\":{\"osId\":\"%s\"}}"
                        .formatted(messageId, osId, osId)));
        assertThrows(MensagemInvalidaException.class, () -> tratador.tratar(
                "{\"messageId\":\"%s\",\"type\":\"CancelarOrcamento\",\"version\":1,\"osId\":\"%s\",\"payload\":{}}"
                        .formatted(messageId, osId)));
    }

    @Test void jsonIlegivelEhRejeitado() {
        assertThrows(MensagemInvalidaException.class, () -> tratador.tratar("isto nao e json"));
    }

    @Test void payloadComTipoErradoEhRejeitado() {
        assertThrows(MensagemInvalidaException.class, () -> tratador.tratar(envelope("GerarOrcamento", 1,
                "{\"osId\":\"%s\",\"clienteId\":\"nao-e-uuid\",\"itens\":[]}".formatted(osId))));
    }

    @Test void falhaDoCasoDeUsoSobeParaAMensagemVoltarParaAFila() {
        doThrow(new IllegalArgumentException("item sem valor")).when(gerar).executar(any());
        assertThrows(IllegalArgumentException.class,
                () -> tratador.tratar(envelope("GerarOrcamento", 1, gerarPayload(osId.toString()))));
    }
}
