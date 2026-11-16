package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.domain.MensagemProcessadaRepository;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import br.com.fiap.soat.mecanica.billing.domain.WebhookEventoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

class MensageriaPersistenciaTest extends PersistenciaTestBase {
    @Autowired OutboxRepository outbox;
    @Autowired MensagemProcessadaRepository mensagens;
    @Autowired WebhookEventoRepository webhooks;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired TransactionTemplate transacao;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;

    @BeforeEach void limpaOutbox() { jdbc.sql("DELETE FROM outbox").update(); }

    private static String envelope(UUID eventId) {
        return "{\"messageId\":\"" + eventId + "\",\"type\":\"OrcamentoGerado\",\"version\":1}";
    }

    @Test void outboxEntregaPendentesEmOrdemAtePublicar() throws Exception {
        UUID antigo = UUID.randomUUID();
        UUID recente = UUID.randomUUID();
        outbox.registrar(recente, "OrcamentoAprovado", envelope(recente), CRIADO.plusSeconds(10));
        outbox.registrar(antigo, "OrcamentoGerado", envelope(antigo), CRIADO);

        var pendentes = outbox.buscarPendentes(10);
        assertEquals(List.of(antigo, recente), pendentes.stream().map(OutboxRepository.Pendente::eventId).toList());
        assertEquals("OrcamentoGerado", pendentes.get(0).tipo());
        assertEquals(CRIADO, pendentes.get(0).criadoEm());
        assertEquals(mapper.readTree(envelope(antigo)), mapper.readTree(pendentes.get(0).envelope()));
        assertEquals(1, outbox.buscarPendentes(1).size());

        outbox.marcarPublicado(antigo, CRIADO.plusSeconds(20));
        assertEquals(List.of(recente), outbox.buscarPendentes(10).stream()
                .map(OutboxRepository.Pendente::eventId).toList());
    }

    @Test void eventoEEstadoSaemJuntosOuNaoSaem() {
        var novo = orcamento(UUID.randomUUID());
        UUID eventId = UUID.randomUUID();
        transacao.executeWithoutResult(status -> {
            orcamentos.inserir(novo);
            outbox.registrar(eventId, "OrcamentoGerado", envelope(eventId), CRIADO);
            status.setRollbackOnly();
        });
        assertTrue(orcamentos.buscarPorId(novo.id()).isEmpty());
        assertTrue(outbox.buscarPendentes(10).isEmpty());

        transacao.executeWithoutResult(status -> {
            orcamentos.inserir(novo);
            outbox.registrar(eventId, "OrcamentoGerado", envelope(eventId), CRIADO);
        });
        assertTrue(orcamentos.buscarPorId(novo.id()).isPresent());
        assertEquals(1, outbox.buscarPendentes(10).size());
    }

    @Test void doisPublicadoresNaoPegamOMesmoEvento() {
        UUID eventId = UUID.randomUUID();
        outbox.registrar(eventId, "OrcamentoGerado", envelope(eventId), CRIADO);
        transacao.executeWithoutResult(status -> {
            assertEquals(1, outbox.buscarPendentes(10).size());
            int vistosPeloOutro = CompletableFuture.supplyAsync(
                    () -> transacao.execute(outro -> outbox.buscarPendentes(10).size())).join();
            assertEquals(0, vistosPeloOutro);
        });
        assertEquals(1, outbox.buscarPendentes(10).size());
    }

    @Test void mensagemRepetidaEReconhecida() {
        UUID messageId = UUID.randomUUID();
        assertTrue(mensagens.registrar(messageId, CRIADO));
        assertFalse(mensagens.registrar(messageId, CRIADO.plusSeconds(5)));
    }

    @Test void webhookSoDeixaDeSerProcessadoDepoisDeMarcado() {
        String eventId = "evt-" + UUID.randomUUID();
        assertTrue(webhooks.registrarRecebimento(eventId, "payment-1", CRIADO));
        // Redelivery after a failed attempt must still be processed.
        assertTrue(webhooks.registrarRecebimento(eventId, "payment-1", CRIADO.plusSeconds(5)));
        webhooks.marcarProcessado(eventId, CRIADO.plusSeconds(6));
        assertFalse(webhooks.registrarRecebimento(eventId, "payment-1", CRIADO.plusSeconds(7)));
    }
}
