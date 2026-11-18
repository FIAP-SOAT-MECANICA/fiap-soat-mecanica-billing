package br.com.fiap.soat.mecanica.billing.application.outbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository.Pendente;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class EsvaziarOutboxUseCaseTest {
    static final Instant AGORA = Instant.parse("2026-02-01T12:00:00Z");

    final OutboxRepository outbox = mock(OutboxRepository.class);
    final PublicadorEventos publicador = mock(PublicadorEventos.class);
    final EsvaziarOutboxUseCase useCase =
            new EsvaziarOutboxUseCase(outbox, publicador, Clock.fixed(AGORA, ZoneOffset.UTC));

    static Pendente evento(String tipo) {
        UUID id = UUID.randomUUID();
        return new Pendente(id, tipo, "{\"messageId\":\"" + id + "\",\"type\":\"" + tipo + "\"}", AGORA.minusSeconds(5));
    }

    @Test void publicaNaOrdemEMarcaCadaEventoPublicado() {
        var a = evento("OrcamentoAprovado");
        var b = evento("OrcamentoRecusado");
        when(outbox.buscarPendentes(10)).thenReturn(List.of(a, b));

        assertEquals(2, useCase.executar(10));

        InOrder ordem = inOrder(publicador, outbox);
        ordem.verify(publicador).publicar(a.tipo(), a.envelope());
        ordem.verify(outbox).marcarPublicado(a.eventId(), AGORA);
        ordem.verify(publicador).publicar(b.tipo(), b.envelope());
        ordem.verify(outbox).marcarPublicado(b.eventId(), AGORA);
    }

    @Test void semPendentesNaoFazNada() {
        when(outbox.buscarPendentes(10)).thenReturn(List.of());
        assertEquals(0, useCase.executar(10));
        verifyNoInteractions(publicador);
        verify(outbox, never()).marcarPublicado(any(), any());
    }

    @Test void falhaInterrompeOLoteMantendoOQueJaSaiu() {
        var a = evento("OrcamentoAprovado");
        var b = evento("OrcamentoRecusado");
        var c = evento("OrcamentoAprovado");
        when(outbox.buscarPendentes(10)).thenReturn(List.of(a, b, c));
        doThrow(new FalhaPublicacaoException("fora do ar", new RuntimeException()))
                .when(publicador).publicar(b.tipo(), b.envelope());

        assertEquals(1, useCase.executar(10));

        verify(outbox).marcarPublicado(a.eventId(), AGORA);
        // The failed event and everything after it stay pending, so order is preserved.
        verify(outbox, never()).marcarPublicado(eq(b.eventId()), any());
        verify(outbox, never()).marcarPublicado(eq(c.eventId()), any());
        verify(publicador, never()).publicar(c.tipo(), c.envelope());
    }
}
