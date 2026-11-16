package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import br.com.fiap.soat.mecanica.billing.domain.MensagemProcessadaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMensagemProcessadaRepository implements MensagemProcessadaRepository {
    private final JdbcClient jdbc;

    JdbcMensagemProcessadaRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean registrar(UUID messageId, Instant processadaEm) {
        return jdbc.sql("INSERT INTO mensagem_processada (message_id, processada_em) "
                        + "VALUES (:messageId, :processadaEm) ON CONFLICT (message_id) DO NOTHING")
                .param("messageId", messageId).param("processadaEm", Tempo.paraBanco(processadaEm))
                .update() == 1;
    }
}
