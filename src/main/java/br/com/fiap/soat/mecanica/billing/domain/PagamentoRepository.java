package br.com.fiap.soat.mecanica.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository {
    /** Stored charge attempt plus the checkout link and lock version kept only by the store. */
    record Registro(Pagamento pagamento, String linkPagamento, long versao) { }

    /**
     * Stores a new charge attempt; {@code linkPagamento} may be null.
     * @throws RegistroDuplicadoException if the id or the external reference already exists
     */
    Registro inserir(Pagamento pagamento, String linkPagamento);

    Optional<Registro> buscarPorId(UUID id);

    Optional<Registro> buscarPorReferenciaExterna(String referenciaExterna);

    /** Charge attempts of the OS, newest first. */
    List<Registro> listarPorOs(UUID osId);

    /**
     * Persists the decision of a payment read at {@code versaoEsperada}.
     * @throws ConflitoDeVersaoException if the stored version differs or the payment is gone
     */
    Registro atualizar(Pagamento pagamento, long versaoEsperada);
}
