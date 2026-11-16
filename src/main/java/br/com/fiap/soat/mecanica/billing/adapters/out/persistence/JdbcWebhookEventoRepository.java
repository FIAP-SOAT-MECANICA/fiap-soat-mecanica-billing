package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import br.com.fiap.soat.mecanica.billing.domain.WebhookEventoRepository;
import java.time.Instant;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcWebhookEventoRepository implements WebhookEventoRepository {
    private final JdbcClient jdbc;

    JdbcWebhookEventoRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean registrarRecebimento(String providerEventId, String paymentId, Instant recebidoEm) {
        jdbc.sql("INSERT INTO webhook_eventos (provider_event_id, payment_id, recebido_em) "
                        + "VALUES (:id, :paymentId, :recebidoEm) ON CONFLICT (provider_event_id) DO NOTHING")
                .param("id", providerEventId).param("paymentId", paymentId)
                .param("recebidoEm", Tempo.paraBanco(recebidoEm)).update();
        return jdbc.sql("SELECT processado_em IS NULL FROM webhook_eventos "
                        + "WHERE provider_event_id = :id FOR UPDATE")
                .param("id", providerEventId).query(Boolean.class).single();
    }

    @Override
    public void marcarProcessado(String providerEventId, Instant processadoEm) {
        jdbc.sql("UPDATE webhook_eventos SET processado_em = :processadoEm "
                        + "WHERE provider_event_id = :id AND processado_em IS NULL")
                .param("processadoEm", Tempo.paraBanco(processadoEm)).param("id", providerEventId).update();
    }
}
