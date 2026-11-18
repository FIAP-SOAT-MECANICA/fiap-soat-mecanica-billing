package br.com.fiap.soat.mecanica.billing.adapters.out.messaging;

import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.application.outbox.FalhaPublicacaoException;
import org.junit.jupiter.api.Test;

class SnsPublicadorEventosTest {
    @Test void entregaOEnvelopeIntactoComOTipoComoAtributo() {
        var canal = LocalStackSupport.novoCanal("sns-adapter");
        var publicador = new SnsPublicadorEventos(LocalStackSupport.sns, canal.topicArn());
        String envelope = "{\"messageId\":\"m-1\",\"type\":\"OrcamentoAprovado\",\"payload\":{\"osId\":\"os-1\"}}";

        publicador.publicar("OrcamentoAprovado", envelope);

        var mensagens = LocalStackSupport.receber(canal.queueUrl(), 10);
        assertEquals(1, mensagens.size());
        assertEquals(envelope, mensagens.get(0).body());
        assertEquals("OrcamentoAprovado", mensagens.get(0).messageAttributes().get("type").stringValue());
    }

    @Test void topicoInexistenteViraFalhaDePublicacao() {
        var arn = LocalStackSupport.novoCanal("sns-adapter-erro").topicArn() + "-inexistente";
        var publicador = new SnsPublicadorEventos(LocalStackSupport.sns, arn);
        assertThrows(FalhaPublicacaoException.class, () -> publicador.publicar("OrcamentoAprovado", "{}"));
    }
}
