package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import static org.junit.jupiter.api.Assertions.*;

import br.com.fiap.soat.mecanica.billing.domain.ConflitoDeVersaoException;
import org.junit.jupiter.api.Test;

class TratadorDeErrosTest {
    @Test void decisaoConcorrenteViraConflitoSemVazarDetalheInterno() {
        var resposta = new TratadorDeErros().conflito(new ConflitoDeVersaoException("orcamento x alterado"));
        assertEquals(409, resposta.getStatusCode().value());
        assertEquals(409, resposta.getBody().status());
        assertFalse(resposta.getBody().mensagem().contains("orcamento x"));
    }
}
