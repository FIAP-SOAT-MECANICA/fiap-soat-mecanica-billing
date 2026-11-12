package br.com.fiap.soat.mecanica.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One charge attempt; a rejected attempt requires a new payment ID. */
public record Pagamento(UUID id, UUID orcamentoId, UUID osId, BigDecimal valor,
                        String referenciaExterna, EstadoPagamento estado,
                        Instant criadoEm, Instant decididoEm) {
    public Pagamento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orcamentoId, "orcamentoId");
        Objects.requireNonNull(osId, "osId");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(criadoEm, "criadoEm");
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("valor invalido");
        valor = valor.setScale(2, java.math.RoundingMode.UNNECESSARY);
        if (referenciaExterna == null || referenciaExterna.isBlank())
            throw new IllegalArgumentException("referenciaExterna obrigatoria");
        if ((estado == EstadoPagamento.PENDENTE) != (decididoEm == null))
            throw new IllegalArgumentException("instante da decisao incoerente");
        if (decididoEm != null && decididoEm.isBefore(criadoEm))
            throw new IllegalArgumentException("decisao anterior a criacao");
    }

    public static Pagamento criar(UUID id, Orcamento orcamento, String referenciaExterna, Instant criadoEm) {
        if (orcamento.estado() != EstadoOrcamento.APROVADO)
            throw new IllegalStateException("orcamento deve estar aprovado");
        return new Pagamento(id, orcamento.id(), orcamento.osId(), orcamento.valorTotal(),
                referenciaExterna, EstadoPagamento.PENDENTE, criadoEm, null);
    }

    public Pagamento confirmar(Instant agora) { return decidir(EstadoPagamento.APROVADO, agora); }
    public Pagamento recusar(Instant agora) { return decidir(EstadoPagamento.RECUSADO, agora); }

    private Pagamento decidir(EstadoPagamento novo, Instant agora) {
        Objects.requireNonNull(agora, "agora");
        if (estado == novo) return this; // Repeated webhook notification.
        if (estado != EstadoPagamento.PENDENTE)
            throw new IllegalStateException("pagamento ja decidido");
        return new Pagamento(id, orcamentoId, osId, valor, referenciaExterna, novo, criadoEm, agora);
    }
}
