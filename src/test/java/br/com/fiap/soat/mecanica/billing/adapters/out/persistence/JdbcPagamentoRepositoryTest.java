package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.domain.ConflitoDeVersaoException;
import br.com.fiap.soat.mecanica.billing.domain.EstadoPagamento;
import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.RegistroDuplicadoException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class JdbcPagamentoRepositoryTest extends PersistenciaTestBase {
    @Autowired OrcamentoRepository orcamentos;
    @Autowired PagamentoRepository pagamentos;

    private Orcamento aprovado() {
        return orcamentos.inserir(orcamento(UUID.randomUUID()).aprovar(CRIADO)).orcamento();
    }

    private static String referencia() { return "mp-" + UUID.randomUUID(); }

    @Test void gravaELeACobrancaComLink() {
        var orcamento = aprovado();
        var novo = Pagamento.criar(UUID.randomUUID(), orcamento, referencia(), CRIADO.plusSeconds(60));
        var gravado = pagamentos.inserir(novo, "https://pagar.exemplo/abc");
        assertEquals(novo, gravado.pagamento());
        assertEquals("https://pagar.exemplo/abc", gravado.linkPagamento());
        assertEquals(0, gravado.versao());
        assertEquals(gravado, pagamentos.buscarPorId(novo.id()).orElseThrow());
        assertEquals(gravado, pagamentos.buscarPorReferenciaExterna(novo.referenciaExterna()).orElseThrow());
        assertTrue(pagamentos.buscarPorId(UUID.randomUUID()).isEmpty());
        assertTrue(pagamentos.buscarPorReferenciaExterna("mp-inexistente").isEmpty());
    }

    @Test void decisaoExigeAVersaoLidaEGravaOInstante() {
        var lido = pagamentos.inserir(
                Pagamento.criar(UUID.randomUUID(), aprovado(), referencia(), CRIADO.plusSeconds(60)), null);
        assertNull(lido.linkPagamento());
        var decidido = lido.pagamento().confirmar(CRIADO.plusSeconds(120));
        var confirmado = pagamentos.atualizar(decidido, lido.versao());
        assertEquals(EstadoPagamento.APROVADO, confirmado.pagamento().estado());
        assertEquals(CRIADO.plusSeconds(120), confirmado.pagamento().decididoEm());
        assertEquals(1, confirmado.versao());

        assertThrows(ConflitoDeVersaoException.class, () -> pagamentos.atualizar(
                lido.pagamento().recusar(CRIADO.plusSeconds(130)), lido.versao()));
        assertEquals(confirmado, pagamentos.buscarPorId(lido.pagamento().id()).orElseThrow());
    }

    @Test void novaTentativaAposRecusaListaDaMaisRecenteParaAMaisAntiga() {
        var orcamento = aprovado();
        var primeira = pagamentos.inserir(
                Pagamento.criar(UUID.randomUUID(), orcamento, referencia(), CRIADO.plusSeconds(60)), null);
        pagamentos.atualizar(primeira.pagamento().recusar(CRIADO.plusSeconds(70)), primeira.versao());
        var segunda = pagamentos.inserir(
                Pagamento.criar(UUID.randomUUID(), orcamento, referencia(), CRIADO.plusSeconds(80)), null);
        var tentativas = pagamentos.listarPorOs(orcamento.osId());
        assertEquals(List.of(segunda.pagamento().id(), primeira.pagamento().id()),
                tentativas.stream().map(r -> r.pagamento().id()).toList());
        assertEquals(EstadoPagamento.RECUSADO, tentativas.get(1).pagamento().estado());
    }

    @Test void rejeitaReferenciaRepetidaEOrcamentoInexistente() {
        var orcamento = aprovado();
        String referencia = referencia();
        pagamentos.inserir(Pagamento.criar(UUID.randomUUID(), orcamento, referencia, CRIADO.plusSeconds(60)), null);
        assertThrows(RegistroDuplicadoException.class, () -> pagamentos.inserir(
                Pagamento.criar(UUID.randomUUID(), orcamento, referencia, CRIADO.plusSeconds(61)), null));

        var naoGravado = orcamento(UUID.randomUUID()).aprovar(CRIADO);
        assertThrows(DataIntegrityViolationException.class, () -> pagamentos.inserir(
                Pagamento.criar(UUID.randomUUID(), naoGravado, referencia(), CRIADO.plusSeconds(60)), null));
    }
}
