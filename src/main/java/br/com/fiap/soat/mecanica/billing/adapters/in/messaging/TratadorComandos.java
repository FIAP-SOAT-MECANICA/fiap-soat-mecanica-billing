package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import br.com.fiap.soat.mecanica.billing.application.orcamento.CancelarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.GerarOrcamentoUseCase;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Validates the OS envelope and routes each command to its use case by {@code type}. */
public class TratadorComandos {
    static final String GERAR_ORCAMENTO = "GerarOrcamento";
    static final String CANCELAR_ORCAMENTO = "CancelarOrcamento";
    static final int VERSAO_SUPORTADA = 1;

    private static final Logger log = LoggerFactory.getLogger(TratadorComandos.class);

    record Envelope(UUID messageId, String type, int version, UUID sagaId, UUID osId, String correlationId,
                    UUID causationId, Instant occurredAt, JsonNode payload) { }

    record PecaPayload(UUID pecaId, int qtd, BigDecimal valorUnit) { }

    record ItemPayload(UUID servicoId, String nomeServico, BigDecimal precoMdo, List<PecaPayload> pecas) { }

    record GerarOrcamentoPayload(UUID osId, UUID clienteId, List<ItemPayload> itens) { }

    record ReferenciaPayload(UUID osId, UUID orcamentoId) { }

    private final ObjectMapper mapper;
    private final GerarOrcamentoUseCase gerar;
    private final CancelarOrcamentoUseCase cancelar;

    public TratadorComandos(ObjectMapper mapper, GerarOrcamentoUseCase gerar, CancelarOrcamentoUseCase cancelar) {
        this.mapper = mapper;
        this.gerar = gerar;
        this.cancelar = cancelar;
    }

    /**
     * @return true when the message can leave the queue; false when no handler exists for its type yet, so it
     *         stays and, after the redrive limit, lands in the dead-letter queue where it can be inspected
     * @throws RuntimeException when the message is malformed or the use case fails; the message must come back
     */
    public boolean tratar(String corpo) {
        Envelope envelope = ler(corpo);
        switch (envelope.type()) {
            case GERAR_ORCAMENTO -> {
                var p = payload(envelope, GerarOrcamentoPayload.class);
                gerar.executar(new GerarOrcamentoUseCase.Comando(envelope.messageId(), envelope.sagaId(),
                        envelope.correlationId(), envelope.osId(), p.clienteId(), itens(p.itens())));
            }
            case CANCELAR_ORCAMENTO -> {
                var p = payload(envelope, ReferenciaPayload.class);
                cancelar.executar(new CancelarOrcamentoUseCase.Comando(envelope.messageId(), envelope.osId(),
                        p.orcamentoId()));
            }
            default -> {
                log.warn("Comando sem tratador; mensagem mantida na fila. type={} messageId={}",
                        envelope.type(), envelope.messageId());
                return false;
            }
        }
        return true;
    }

    private Envelope ler(String corpo) {
        Envelope envelope;
        try {
            envelope = mapper.readValue(corpo, Envelope.class);
        } catch (JsonProcessingException e) {
            throw new MensagemInvalidaException("envelope ilegivel: " + e.getOriginalMessage(), e);
        }
        if (envelope.version() != VERSAO_SUPORTADA)
            throw new MensagemInvalidaException("versao de envelope nao suportada: " + envelope.version());
        exigir(envelope.messageId(), "messageId");
        exigir(envelope.osId(), "osId");
        exigir(envelope.payload(), "payload");
        if (envelope.type() == null || envelope.type().isBlank()) throw new MensagemInvalidaException("type ausente");
        JsonNode osDoPayload = envelope.payload().get("osId");
        if (osDoPayload == null || !envelope.osId().toString().equals(osDoPayload.asText()))
            throw new MensagemInvalidaException("payload.osId diferente de osId do envelope");
        return envelope;
    }

    private <T> T payload(Envelope envelope, Class<T> tipo) {
        try {
            return mapper.treeToValue(envelope.payload(), tipo);
        } catch (JsonProcessingException e) {
            throw new MensagemInvalidaException("payload de " + envelope.type() + " invalido: "
                    + e.getOriginalMessage(), e);
        }
    }

    private static List<GerarOrcamentoUseCase.Item> itens(List<ItemPayload> itens) {
        if (itens == null) return null;
        return itens.stream().map(i -> new GerarOrcamentoUseCase.Item(i.servicoId(), i.nomeServico(), i.precoMdo(),
                i.pecas() == null ? List.of() : i.pecas().stream()
                        .map(p -> new GerarOrcamentoUseCase.Peca(p.pecaId(), p.qtd(), p.valorUnit())).toList()))
                .toList();
    }

    private static void exigir(Object valor, String campo) {
        if (valor == null) throw new MensagemInvalidaException("campo obrigatorio ausente: " + campo);
    }
}
