package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.domain.ConflitoDeVersaoException;
import br.com.fiap.soat.mecanica.billing.domain.EstadoOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.RegistroDuplicadoException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcOrcamentoRepositoryTest extends PersistenciaTestBase {
    @Autowired OrcamentoRepository orcamentos;
    @Autowired JdbcClient jdbc;

    @Test void gravaELeOAgregadoComSnapshotIntacto() {
        var novo = orcamento(UUID.randomUUID());
        var gravado = orcamentos.inserir(novo);
        assertEquals(novo, gravado.orcamento());
        assertEquals(1, gravado.revisao());
        assertEquals(0, gravado.versao());
        assertEquals(gravado, orcamentos.buscarPorId(novo.id()).orElseThrow());
        assertTrue(orcamentos.buscarPorId(UUID.randomUUID()).isEmpty());
        assertTrue(orcamentos.buscarUltimoPorOs(UUID.randomUUID()).isEmpty());
    }

    @Test void novaRevisaoSoNasceDepoisQueAVigenteTermina() {
        UUID osId = UUID.randomUUID();
        var primeira = orcamentos.inserir(orcamento(osId));
        assertThrows(RegistroDuplicadoException.class, () -> orcamentos.inserir(orcamento(osId)));

        orcamentos.atualizar(primeira.orcamento().cancelar(), primeira.versao());
        var segunda = orcamentos.inserir(orcamento(osId));
        assertEquals(2, segunda.revisao());
        assertEquals(segunda, orcamentos.buscarUltimoPorOs(osId).orElseThrow());
        var todas = orcamentos.listarPorOs(osId);
        assertEquals(2, todas.size());
        assertEquals(EstadoOrcamento.CANCELADO, todas.get(0).orcamento().estado());
        assertEquals(segunda, todas.get(1));
    }

    @Test void rejeitaIdRepetido() {
        var novo = orcamento(UUID.randomUUID());
        orcamentos.inserir(novo);
        assertThrows(RegistroDuplicadoException.class, () -> orcamentos.inserir(novo));
    }

    @Test void transicaoExigeAVersaoLida() {
        var lido = orcamentos.inserir(orcamento(UUID.randomUUID()));
        var aprovado = orcamentos.atualizar(lido.orcamento().aprovar(CRIADO), lido.versao());
        assertEquals(EstadoOrcamento.APROVADO, aprovado.orcamento().estado());
        assertEquals(1, aprovado.versao());
        assertEquals(lido.revisao(), aprovado.revisao());

        // A second writer that still holds the old read must not overwrite the approval.
        assertThrows(ConflitoDeVersaoException.class,
                () -> orcamentos.atualizar(lido.orcamento().recusar(), lido.versao()));
        assertEquals(aprovado, orcamentos.buscarPorId(lido.orcamento().id()).orElseThrow());
        assertThrows(ConflitoDeVersaoException.class,
                () -> orcamentos.atualizar(orcamento(UUID.randomUUID()), 0));
    }

    @Test void snapshotCorrompidoFalhaNaLeitura() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO orcamentos (id, os_id, cliente_id, revisao, estado, valor_total,
                    itens_snapshot, criado_em, expira_em)
                VALUES (:id, :osId, :clienteId, 1, 'GERADO', 50.00, '[{"servicoId":"x"}]',
                    now(), now() + interval '1 day')
                """).param("id", id).param("osId", UUID.randomUUID()).param("clienteId", UUID.randomUUID())
                .update();
        assertThrows(IllegalStateException.class, () -> orcamentos.buscarPorId(id));
    }
}
