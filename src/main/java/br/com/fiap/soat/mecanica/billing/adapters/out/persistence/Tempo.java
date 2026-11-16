package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** TIMESTAMPTZ holds microseconds; truncating on write keeps stored and in-memory order equal. */
final class Tempo {
    private Tempo() { }

    static OffsetDateTime paraBanco(Instant instante) {
        return instante == null ? null : instante.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    static Instant doBanco(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime valor = rs.getObject(coluna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
