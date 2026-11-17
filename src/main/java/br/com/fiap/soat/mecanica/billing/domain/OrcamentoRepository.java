package br.com.fiap.soat.mecanica.billing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository {
    /** Stored aggregate plus what only the store knows: revision within the OS and lock version. */
    record Registro(Orcamento orcamento, int revisao, long versao) { }

    /**
     * Stores a new budget as the next revision of its OS.
     * @throws RegistroDuplicadoException if the id exists, the OS already has a budget in
     *         GERADO or APROVADO, or a concurrent insert took the same revision
     */
    Registro inserir(Orcamento orcamento);

    Optional<Registro> buscarPorId(UUID id);

    /** Highest revision of the OS, whatever its state. */
    Optional<Registro> buscarUltimoPorOs(UUID osId);

    /** All revisions of the OS, oldest first. */
    List<Registro> listarPorOs(UUID osId);

    /**
     * Persists a state transition of a budget read at {@code versaoEsperada}.
     * @throws ConflitoDeVersaoException if the stored version differs or the budget is gone
     */
    Registro atualizar(Orcamento orcamento, long versaoEsperada);
}
