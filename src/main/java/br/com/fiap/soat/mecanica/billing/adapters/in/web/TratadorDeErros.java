package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import br.com.fiap.soat.mecanica.billing.application.AcessoNegadoException;
import br.com.fiap.soat.mecanica.billing.application.orcamento.OrcamentoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.application.orcamento.TransicaoInvalidaException;
import br.com.fiap.soat.mecanica.billing.domain.ConflitoDeVersaoException;
import br.com.fiap.soat.mecanica.billing.domain.FalhaCobrancaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Error body with the same leading fields as the OS service: status, erro and mensagem. */
@RestControllerAdvice
class TratadorDeErros {
    record Erro(int status, String erro, String mensagem) { }

    @ExceptionHandler(OrcamentoNaoEncontradoException.class)
    ResponseEntity<Erro> naoEncontrado(OrcamentoNaoEncontradoException e) {
        return responder(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    ResponseEntity<Erro> acessoNegado(AcessoNegadoException e) {
        return responder(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(TransicaoInvalidaException.class)
    ResponseEntity<Erro> transicaoInvalida(TransicaoInvalidaException e) {
        return responder(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ConflitoDeVersaoException.class)
    ResponseEntity<Erro> conflito(ConflitoDeVersaoException e) {
        return responder(HttpStatus.CONFLICT, "Registro alterado por outra requisição; consulte e tente de novo");
    }

    @ExceptionHandler(FalhaCobrancaException.class)
    ResponseEntity<Erro> provedorIndisponivel(FalhaCobrancaException e) {
        // Also what makes the payment provider retry a notification.
        return responder(HttpStatus.BAD_GATEWAY, "Provedor de pagamento indisponível");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Erro> parametroInvalido(MethodArgumentTypeMismatchException e) {
        return responder(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + e.getName());
    }

    private static ResponseEntity<Erro> responder(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new Erro(status.value(), status.getReasonPhrase(), mensagem));
    }
}
