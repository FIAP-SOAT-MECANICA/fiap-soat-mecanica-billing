package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import br.com.fiap.soat.mecanica.billing.domain.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOutboxRepository implements OutboxRepository {
    private final JdbcClient jdbc;

    JdbcOutboxRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public void registrar(UUID eventId, String tipo, String envelope, Instant criadoEm) {
        jdbc.sql("INSERT INTO outbox (event_id, tipo, envelope, criado_em) "
                        + "VALUES (:eventId, :tipo, CAST(:envelope AS jsonb), :criadoEm)")
                .param("eventId", eventId).param("tipo", tipo).param("envelope", envelope)
                .param("criadoEm", Tempo.paraBanco(criadoEm)).update();
    }

    @Override
    public List<Pendente> buscarPendentes(int limite) {
        return jdbc.sql("SELECT event_id, tipo, envelope::text AS envelope, criado_em FROM outbox "
                        + "WHERE publicado_em IS NULL ORDER BY criado_em LIMIT :limite FOR UPDATE SKIP LOCKED")
                .param("limite", limite)
                .query((rs, linha) -> new Pendente(rs.getObject("event_id", UUID.class), rs.getString("tipo"),
                        rs.getString("envelope"), Tempo.doBanco(rs, "criado_em")))
                .list();
    }

    @Override
    public void marcarPublicado(UUID eventId, Instant publicadoEm) {
        jdbc.sql("UPDATE outbox SET publicado_em = :publicadoEm WHERE event_id = :eventId AND publicado_em IS NULL")
                .param("publicadoEm", Tempo.paraBanco(publicadoEm)).param("eventId", eventId).update();
    }
}
