package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.application.ResultadoComando;
import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemPeca;
import br.com.fiap.soat.mecanica.billing.domain.MensagemProcessadaRepository;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository.Registro;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GerarOrcamento command from the OS: stores the budget and answers with OrcamentoGerado. */
@Service
public class GerarOrcamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(GerarOrcamentoUseCase.class);

    public record Peca(UUID pecaId, int quantidade, BigDecimal valorUnitario) { }

    public record Item(UUID servicoId, String nomeServico, BigDecimal precoMdo, List<Peca> pecas) { }

    /** What travels in the command envelope plus the payload. */
    public record Comando(UUID messageId, UUID sagaId, String correlationId, UUID osId, UUID clienteId,
                          List<Item> itens) { }

    private final MensagemProcessadaRepository inbox;
    private final OrcamentoRepository orcamentos;
    private final EventosOrcamento eventos;
    private final Clock relogio;
    private final Duration validade;

    public GerarOrcamentoUseCase(MensagemProcessadaRepository inbox, OrcamentoRepository orcamentos,
                                 EventosOrcamento eventos, Clock relogio,
                                 @Value("${billing.orcamento.validade-dias:7}") long validadeDias) {
        this.inbox = inbox;
        this.orcamentos = orcamentos;
        this.eventos = eventos;
        this.relogio = relogio;
        this.validade = Duration.ofDays(validadeDias);
    }

    /**
     * Inbox, budget and event commit together. An invalid command throws, rolling the inbox row back,
     * so the broker redelivers it and eventually moves it to the dead-letter queue.
     */
    @Transactional
    public ResultadoComando executar(Comando comando) {
        Instant agora = relogio.instant();
        if (!inbox.registrar(comando.messageId(), agora)) return ResultadoComando.DUPLICADO;

        // A repeated command for the same OS (new message id) must not create a second budget:
        // the OS only needs to learn the id of the one that exists.
        var vigente = orcamentos.buscarUltimoPorOs(comando.osId()).map(Registro::orcamento)
                .filter(o -> o.estado() == EstadoOrcamento.GERADO || o.estado() == EstadoOrcamento.APROVADO);
        if (vigente.isPresent()) {
            Orcamento existente = vigente.get();
            log.info("OS {} ja tem orcamento vigente {}; nenhum novo foi criado", comando.osId(), existente.id());
            if (existente.estado() == EstadoOrcamento.GERADO)
                eventos.gerado(existente, comando.sagaId(), comando.messageId(), comando.correlationId(), agora);
            return ResultadoComando.PROCESSADO;
        }

        Orcamento novo = Orcamento.gerar(UUID.randomUUID(), comando.osId(), comando.clienteId(),
                converter(comando.itens()), agora, agora.plus(validade));
        Orcamento gravado = orcamentos.inserir(novo).orcamento();
        eventos.gerado(gravado, comando.sagaId(), comando.messageId(), comando.correlationId(), agora);
        return ResultadoComando.PROCESSADO;
    }

    private static List<ItemOrcamento> converter(List<Item> itens) {
        if (itens == null) throw new IllegalArgumentException("itens obrigatorios");
        return itens.stream().map(item -> new ItemOrcamento(UUID.randomUUID(), item.servicoId(), item.nomeServico(),
                duasCasas(item.precoMdo()),
                item.pecas() == null ? List.<ItemPeca>of() : item.pecas().stream()
                        .map(p -> new ItemPeca(p.pecaId(), p.quantidade(), duasCasas(p.valorUnitario()))).toList()))
                .toList();
    }

    /** Money with more than two decimals is rejected instead of rounded: the total is what the customer pays. */
    private static BigDecimal duasCasas(BigDecimal valor) {
        if (valor == null) throw new IllegalArgumentException("valor obrigatorio");
        try {
            return valor.setScale(2, java.math.RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("valor com mais de duas casas decimais: " + valor, e);
        }
    }
}
