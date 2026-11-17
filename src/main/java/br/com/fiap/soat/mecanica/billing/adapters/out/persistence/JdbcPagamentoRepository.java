package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import br.com.fiap.soat.mecanica.billing.domain.ConflitoDeVersaoException;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.RegistroDuplicadoException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPagamentoRepository implements PagamentoRepository {
    private static final String COLUNAS = "id, orcamento_id, os_id, referencia_externa, estado, valor, "
            + "link_pagamento, criado_em, decidido_em, versao";

    private final JdbcClient jdbc;

    JdbcPagamentoRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Override
    public Registro inserir(Pagamento pagamento, String linkPagamento) {
        String sql = """
                INSERT INTO pagamentos (id, orcamento_id, os_id, referencia_externa, estado, valor,
                    link_pagamento, criado_em, decidido_em)
                VALUES (:id, :orcamentoId, :osId, :referencia, :estado, :valor, :link, :criadoEm, :decididoEm)
                RETURNING\s""" + COLUNAS;
        try {
            return jdbc.sql(sql)
                    .param("id", pagamento.id())
                    .param("orcamentoId", pagamento.orcamentoId())
                    .param("osId", pagamento.osId())
                    .param("referencia", pagamento.referenciaExterna())
                    .param("estado", pagamento.estado().name())
                    .param("valor", pagamento.valor())
                    .param("link", linkPagamento, Types.VARCHAR)
                    .param("criadoEm", Tempo.paraBanco(pagamento.criadoEm()))
                    .param("decididoEm", Tempo.paraBanco(pagamento.decididoEm()), Types.TIMESTAMP_WITH_TIMEZONE)
                    .query(this::mapear).single();
        } catch (DuplicateKeyException e) {
            throw new RegistroDuplicadoException("pagamento ou referencia externa ja registrados", e);
        }
    }

    @Override
    public Optional<Registro> buscarPorId(UUID id) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM pagamentos WHERE id = :id")
                .param("id", id).query(this::mapear).optional();
    }

    @Override
    public Optional<Registro> buscarPorReferenciaExterna(String referenciaExterna) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM pagamentos WHERE referencia_externa = :referencia")
                .param("referencia", referenciaExterna).query(this::mapear).optional();
    }

    @Override
    public List<Registro> listarPorOs(UUID osId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM pagamentos WHERE os_id = :osId ORDER BY criado_em DESC")
                .param("osId", osId).query(this::mapear).list();
    }

    @Override
    public Registro atualizar(Pagamento pagamento, long versaoEsperada) {
        String sql = "UPDATE pagamentos SET estado = :estado, decidido_em = :decididoEm, versao = versao + 1 "
                + "WHERE id = :id AND versao = :versao RETURNING " + COLUNAS;
        return jdbc.sql(sql)
                .param("estado", pagamento.estado().name())
                .param("decididoEm", Tempo.paraBanco(pagamento.decididoEm()), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("id", pagamento.id())
                .param("versao", versaoEsperada)
                .query(this::mapear).optional()
                .orElseThrow(() -> new ConflitoDeVersaoException(
                        "pagamento " + pagamento.id() + " alterado por outra transacao"));
    }

    private Registro mapear(ResultSet rs, int linha) throws SQLException {
        var pagamento = new Pagamento(rs.getObject("id", UUID.class), rs.getObject("orcamento_id", UUID.class),
                rs.getObject("os_id", UUID.class), rs.getBigDecimal("valor"), rs.getString("referencia_externa"),
                EstadoPagamento.valueOf(rs.getString("estado")),
                Tempo.doBanco(rs, "criado_em"), Tempo.doBanco(rs, "decidido_em"));
        return new Registro(pagamento, rs.getString("link_pagamento"), rs.getLong("versao"));
    }
}
