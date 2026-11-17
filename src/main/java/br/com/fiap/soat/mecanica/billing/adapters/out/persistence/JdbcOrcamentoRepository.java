package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import br.com.fiap.soat.mecanica.billing.domain.ConflitoDeVersaoException;
import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.RegistroDuplicadoException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOrcamentoRepository implements OrcamentoRepository {
    private static final String COLUNAS = "id, os_id, cliente_id, revisao, estado, valor_total, "
            + "itens_snapshot::text AS itens, criado_em, expira_em, versao";
    private static final TypeReference<List<ItemOrcamento>> ITENS = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    JdbcOrcamentoRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Override
    public Registro inserir(Orcamento orcamento) {
        // The revision is computed in the same statement; uq_orcamentos_os_revisao settles races.
        String sql = """
                INSERT INTO orcamentos (id, os_id, cliente_id, revisao, estado, valor_total,
                    itens_snapshot, criado_em, expira_em)
                SELECT :id, :osId, :clienteId, COALESCE(MAX(revisao), 0) + 1, :estado, :valorTotal,
                    CAST(:itens AS jsonb), :criadoEm, :expiraEm
                FROM orcamentos WHERE os_id = :osId
                RETURNING\s""" + COLUNAS;
        try {
            return jdbc.sql(sql)
                    .param("id", orcamento.id())
                    .param("osId", orcamento.osId())
                    .param("clienteId", orcamento.clienteId())
                    .param("estado", orcamento.estado().name())
                    .param("valorTotal", orcamento.valorTotal())
                    .param("itens", serializar(orcamento.itens()))
                    .param("criadoEm", Tempo.paraBanco(orcamento.criadoEm()))
                    .param("expiraEm", Tempo.paraBanco(orcamento.expiraEm()))
                    .query(this::mapear).single();
        } catch (DuplicateKeyException e) {
            throw new RegistroDuplicadoException("orcamento duplicado ou OS ja possui orcamento vigente", e);
        }
    }

    @Override
    public Optional<Registro> buscarPorId(UUID id) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM orcamentos WHERE id = :id")
                .param("id", id).query(this::mapear).optional();
    }

    @Override
    public Optional<Registro> buscarUltimoPorOs(UUID osId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM orcamentos WHERE os_id = :osId ORDER BY revisao DESC LIMIT 1")
                .param("osId", osId).query(this::mapear).optional();
    }

    @Override
    public List<Registro> listarPorOs(UUID osId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM orcamentos WHERE os_id = :osId ORDER BY revisao")
                .param("osId", osId).query(this::mapear).list();
    }

    @Override
    public Registro atualizar(Orcamento orcamento, long versaoEsperada) {
        // Only the state changes after creation; everything else is an immutable snapshot.
        String sql = "UPDATE orcamentos SET estado = :estado, versao = versao + 1 "
                + "WHERE id = :id AND versao = :versao RETURNING " + COLUNAS;
        return jdbc.sql(sql)
                .param("estado", orcamento.estado().name())
                .param("id", orcamento.id())
                .param("versao", versaoEsperada)
                .query(this::mapear).optional()
                .orElseThrow(() -> new ConflitoDeVersaoException(
                        "orcamento " + orcamento.id() + " alterado por outra transacao"));
    }

    private Registro mapear(ResultSet rs, int linha) throws SQLException {
        var orcamento = new Orcamento(rs.getObject("id", UUID.class), rs.getObject("os_id", UUID.class),
                rs.getObject("cliente_id", UUID.class), desserializar(rs.getString("itens")),
                rs.getBigDecimal("valor_total"), EstadoOrcamento.valueOf(rs.getString("estado")),
                Tempo.doBanco(rs, "criado_em"), Tempo.doBanco(rs, "expira_em"));
        return new Registro(orcamento, rs.getInt("revisao"), rs.getLong("versao"));
    }

    private String serializar(List<ItemOrcamento> itens) {
        try {
            return mapper.writeValueAsString(itens);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("itens do orcamento nao serializaveis", e);
        }
    }

    private List<ItemOrcamento> desserializar(String json) {
        try {
            return mapper.readValue(json, ITENS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("itens_snapshot invalido", e);
        }
    }
}
