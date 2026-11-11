package br.com.fiap.soat.mecanica.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class BillingMigrationTest {
    @Container static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("test");

    @Test void criaSchemaEImpedeDuplicidadeDeRevisaoMensagemEWebhook() throws Exception {
        var result = Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .load().migrate();
        assertEquals(1, result.migrationsExecuted);
        UUID osId = UUID.randomUUID();
        UUID clienteId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
            String insert = """
                    INSERT INTO orcamentos(id, os_id, cliente_id, revisao, estado, valor_total,
                        itens_snapshot, criado_em, expira_em)
                    VALUES (?, ?, ?, 1, 'GERADO', 50.00, '[{"servicoId":"x"}]', now(), now() + interval '1 day')
                    """;
            try (var statement = connection.prepareStatement(insert)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, osId);
                statement.setObject(3, clienteId);
                assertEquals(1, statement.executeUpdate());
                statement.setObject(1, UUID.randomUUID());
                assertThrows(SQLException.class, statement::executeUpdate);
            }
            try (var statement = connection.createStatement()) {
                String budgetId;
                try (var query = statement.executeQuery("SELECT id FROM orcamentos WHERE os_id = '" + osId + "'")) {
                    assertTrue(query.next());
                    budgetId = query.getString(1);
                }
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO pagamentos(id,orcamento_id,os_id,referencia_externa,estado,valor,criado_em) "
                        + "VALUES ('" + UUID.randomUUID() + "','" + budgetId + "','" + UUID.randomUUID()
                        + "','mp-wrong-os','PENDENTE',50.00,now())"));
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO pagamentos(id,orcamento_id,os_id,referencia_externa,estado,valor,criado_em,decidido_em) "
                        + "VALUES ('" + UUID.randomUUID() + "','" + budgetId + "','" + osId
                        + "','mp-time','APROVADO',50.00,now(),now() - interval '1 day')"));
                String messageId = UUID.randomUUID().toString();
                statement.executeUpdate("INSERT INTO mensagem_processada VALUES ('" + messageId + "', now())");
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO mensagem_processada VALUES ('" + messageId + "', now())"));
                statement.executeUpdate("INSERT INTO webhook_eventos VALUES ('evt-1','payment-1',now(),null)");
                assertThrows(SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO webhook_eventos VALUES ('evt-1','payment-1',now(),null)"));
            }
        }
    }
}
