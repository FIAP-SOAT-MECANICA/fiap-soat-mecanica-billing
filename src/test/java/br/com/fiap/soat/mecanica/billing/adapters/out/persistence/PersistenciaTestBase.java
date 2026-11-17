package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import br.com.fiap.soat.mecanica.billing.domain.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemPeca;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** One PostgreSQL and one Spring context shared by every persistence test class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class PersistenciaTestBase {
    static final Instant CRIADO = Instant.parse("2026-01-01T10:00:00Z");
    static final Instant EXPIRA = Instant.parse("2026-01-02T10:00:00Z");

    static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("test");

    static { db.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
    }

    static Orcamento orcamento(UUID osId) {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Troca de filtro",
                new BigDecimal("80.00"), List.of(new ItemPeca(UUID.randomUUID(), 2, new BigDecimal("29.90"))));
        return Orcamento.gerar(UUID.randomUUID(), osId, UUID.randomUUID(), List.of(item), CRIADO, EXPIRA);
    }
}
