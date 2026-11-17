package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemPeca;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;

/** Full stack: real tokens checked by the decoder, real PostgreSQL behind the use cases. */
@SpringBootTest
@AutoConfigureMockMvc
class OrcamentoApiTest {
    static final PostgreSQLContainer<?> db = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("billing").withUsername("billing").withPassword("test");
    static final KeyPair EMISSOR = novoParDeChaves();
    static final KeyPair IMPOSTOR = novoParDeChaves();

    static { db.start(); }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", db::getJdbcUrl);
        registry.add("spring.datasource.username", db::getUsername);
        registry.add("spring.datasource.password", db::getPassword);
        registry.add("billing.jwt.public-key", () -> "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(EMISSOR.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");
    }

    @Autowired MockMvc mvc;
    @Autowired OrcamentoRepository orcamentos;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;

    final UUID cliente = UUID.randomUUID();

    @Test void rotasDeOrcamentoExigemTokenValido() throws Exception {
        UUID id = gravar(vigente()).id();
        mvc.perform(get("/orcamentos/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/orcamentos/" + id).header("Authorization", "Bearer nao-e-jwt"))
                .andExpect(status().isUnauthorized());
        String[] invalidos = {
                token(EMISSOR, cliente.toString(), "CLIENTE", null, c -> c.audience("outra-api")),
                token(EMISSOR, cliente.toString(), "CLIENTE", null, c -> c.issuer("outro-emissor")),
                token(EMISSOR, cliente.toString(), "CLIENTE", null,
                        c -> c.expirationTime(Date.from(Instant.now().minus(Duration.ofHours(1))))),
                token(EMISSOR, cliente.toString(), "CLIENTE", null, c -> c.expirationTime(null)),
                token(EMISSOR, null, "CLIENTE", null, c -> { }),
                token(IMPOSTOR, cliente.toString(), "CLIENTE", null, c -> { })};
        for (String invalido : invalidos)
            mvc.perform(get("/orcamentos/" + id).header("Authorization", "Bearer " + invalido))
                    .andExpect(status().isUnauthorized());
    }

    @Test void somenteClienteEAtendenteAcessam() throws Exception {
        UUID id = gravar(vigente()).id();
        mvc.perform(comToken(get("/orcamentos/" + id), funcionario("MECANICO"))).andExpect(status().isForbidden());
        mvc.perform(comToken(get("/orcamentos/" + id), token(EMISSOR, "x", "OUTRO", null, c -> { })))
                .andExpect(status().isForbidden());
        mvc.perform(comToken(get("/pagamentos/por-os/" + id), funcionario("ATENDENTE")))
                .andExpect(status().isForbidden());
    }

    @Test void rotasPublicasDispensamToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/orcamentos/{id}/aprovar'].patch").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }

    @Test void donoConsultaOProprioOrcamento() throws Exception {
        Orcamento orcamento = gravar(vigente());
        mvc.perform(comToken(get("/orcamentos/" + orcamento.id()), doCliente(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orcamento.id().toString()))
                .andExpect(jsonPath("$.osId").value(orcamento.osId().toString()))
                .andExpect(jsonPath("$.clienteId").value(cliente.toString()))
                .andExpect(jsonPath("$.revisao").value(1))
                .andExpect(jsonPath("$.estado").value("GERADO"))
                .andExpect(jsonPath("$.valorTotal").value(139.80))
                .andExpect(jsonPath("$.itens[0].nomeServico").value("Troca de filtro"))
                .andExpect(jsonPath("$.itens[0].total").value(139.80))
                .andExpect(jsonPath("$.itens[0].pecas[0].quantidade").value(2))
                .andExpect(jsonPath("$.itens[0].pecas[0].total").value(59.80))
                .andExpect(jsonPath("$.expiraEm").value(orcamento.expiraEm().toString()));
    }

    @Test void clienteNaoEnxergaNemDecideOrcamentoAlheio() throws Exception {
        Orcamento orcamento = gravar(vigente());
        String outro = doCliente(UUID.randomUUID());
        mvc.perform(comToken(get("/orcamentos/" + orcamento.id()), outro)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.mensagem").value("Orçamento pertence a outro cliente"));
        mvc.perform(comToken(get("/orcamentos/por-os/" + orcamento.osId()), outro)).andExpect(status().isForbidden());
        mvc.perform(comToken(patch("/orcamentos/" + orcamento.id() + "/aprovar"), outro))
                .andExpect(status().isForbidden());
        mvc.perform(comToken(patch("/orcamentos/" + orcamento.id() + "/recusar"), outro))
                .andExpect(status().isForbidden());
        assertEquals(EstadoOrcamento.GERADO, estado(orcamento.id()));
        assertTrue(eventos(orcamento.id()).isEmpty());
    }

    @Test void listaRevisoesDaOsDaMaisAntigaParaAMaisNova() throws Exception {
        UUID osId = UUID.randomUUID();
        var primeira = orcamentos.inserir(orcamento(osId, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600)));
        orcamentos.atualizar(primeira.orcamento().cancelar(), primeira.versao());
        Orcamento segunda = gravar(orcamento(osId, Instant.now().minusSeconds(30), Instant.now().plusSeconds(3600)));
        mvc.perform(comToken(get("/orcamentos/por-os/" + osId), funcionario("ATENDENTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].estado").value("CANCELADO"))
                .andExpect(jsonPath("$[1].id").value(segunda.id().toString()))
                .andExpect(jsonPath("$[1].revisao").value(2));
        mvc.perform(comToken(get("/orcamentos/por-os/" + UUID.randomUUID()), doCliente(cliente)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test void donoAprovaEOEventoVaiParaAOutbox() throws Exception {
        Orcamento orcamento = gravar(vigente());
        mvc.perform(comToken(patch("/orcamentos/" + orcamento.id() + "/aprovar"), doCliente(cliente))
                        .header("X-Correlation-Id", "req-123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROVADO"));
        assertEquals(EstadoOrcamento.APROVADO, estado(orcamento.id()));

        List<JsonNode> eventos = eventos(orcamento.id());
        assertEquals(1, eventos.size());
        JsonNode envelope = eventos.get(0);
        assertEquals("OrcamentoAprovado", envelope.get("type").asText());
        assertEquals(1, envelope.get("version").asInt());
        assertEquals(orcamento.osId().toString(), envelope.get("osId").asText());
        assertEquals(orcamento.osId().toString(), envelope.get("payload").get("osId").asText());
        assertEquals("req-123", envelope.get("correlationId").asText());
        assertDoesNotThrow(() -> UUID.fromString(envelope.get("messageId").asText()));
        assertDoesNotThrow(() -> Instant.parse(envelope.get("occurredAt").asText()));

        // A decided budget accepts no second decision and emits nothing else.
        mvc.perform(comToken(patch("/orcamentos/" + orcamento.id() + "/aprovar"), doCliente(cliente)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.erro").value("Conflict"));
        mvc.perform(comToken(patch("/orcamentos/" + orcamento.id() + "/recusar"), doCliente(cliente)))
                .andExpect(status().isConflict());
        assertEquals(1, eventos(orcamento.id()).size());
    }

    @Test void atendenteRecusaEmNomeDoCliente() throws Exception {
        Orcamento orcamento = gravar(vigente());
        mvc.perform(comToken(patch("/orcamentos/" + orcamento.id() + "/recusar"), funcionario("ATENDENTE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECUSADO"));
        List<JsonNode> eventos = eventos(orcamento.id());
        assertEquals(1, eventos.size());
        assertEquals("OrcamentoRecusado", eventos.get(0).get("type").asText());
        assertTrue(eventos.get(0).get("correlationId").isNull());
    }

    @Test void orcamentoExpiradoNaoPodeSerAprovado() throws Exception {
        Orcamento expirado = gravar(orcamento(UUID.randomUUID(),
                Instant.now().minus(Duration.ofDays(2)), Instant.now().minus(Duration.ofDays(1))));
        mvc.perform(comToken(patch("/orcamentos/" + expirado.id() + "/aprovar"), doCliente(cliente)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensagem").value("Decisão rejeitada: orcamento expirado"));
        assertEquals(EstadoOrcamento.GERADO, estado(expirado.id()));
        assertTrue(eventos(expirado.id()).isEmpty());
    }

    @Test void idDesconhecidoOuMalformado() throws Exception {
        String atendente = funcionario("ATENDENTE");
        mvc.perform(comToken(get("/orcamentos/" + UUID.randomUUID()), atendente)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(comToken(patch("/orcamentos/" + UUID.randomUUID() + "/aprovar"), atendente))
                .andExpect(status().isNotFound());
        mvc.perform(comToken(get("/orcamentos/nao-e-uuid"), atendente)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Parâmetro inválido: id"));
    }

    private Orcamento vigente() {
        return orcamento(UUID.randomUUID(), Instant.now().minusSeconds(60), Instant.now().plus(Duration.ofDays(1)));
    }

    private Orcamento orcamento(UUID osId, Instant criadoEm, Instant expiraEm) {
        var item = new ItemOrcamento(UUID.randomUUID(), UUID.randomUUID(), "Troca de filtro",
                new BigDecimal("80.00"), List.of(new ItemPeca(UUID.randomUUID(), 2, new BigDecimal("29.90"))));
        return Orcamento.gerar(UUID.randomUUID(), osId, cliente, List.of(item), criadoEm, expiraEm);
    }

    private Orcamento gravar(Orcamento orcamento) { return orcamentos.inserir(orcamento).orcamento(); }

    private EstadoOrcamento estado(UUID id) { return orcamentos.buscarPorId(id).orElseThrow().orcamento().estado(); }

    private List<JsonNode> eventos(UUID orcamentoId) {
        return jdbc.sql("SELECT envelope::text FROM outbox WHERE envelope->'payload'->>'orcamentoId' = :id "
                        + "AND publicado_em IS NULL")
                .param("id", orcamentoId.toString()).query(String.class).list().stream()
                .map(json -> assertDoesNotThrow(() -> mapper.readTree(json))).toList();
    }

    private static MockHttpServletRequestBuilder comToken(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", "Bearer " + token);
    }

    private static String doCliente(UUID id) { return token(EMISSOR, id.toString(), "CLIENTE", null, c -> { }); }

    private static String funcionario(String cargo) {
        return token(EMISSOR, UUID.randomUUID().toString(), "FUNCIONARIO", cargo, c -> { });
    }

    private static String token(KeyPair chaves, String sub, String tipo, String cargo,
                                Consumer<JWTClaimsSet.Builder> ajuste) {
        var claims = new JWTClaimsSet.Builder().subject(sub).issuer("fiap-soat-mecanica-identity")
                .audience("fiap-soat-mecanica").claim("principal_type", tipo).claim("cargo", cargo)
                .expirationTime(Date.from(Instant.now().plus(Duration.ofHours(1))));
        ajuste.accept(claims);
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        assertDoesNotThrow(() -> jwt.sign(new RSASSASigner(chaves.getPrivate())));
        return jwt.serialize();
    }

    private static KeyPair novoParDeChaves() {
        return assertDoesNotThrow(() -> {
            var gerador = KeyPairGenerator.getInstance("RSA");
            gerador.initialize(2048);
            return gerador.generateKeyPair();
        });
    }
}
