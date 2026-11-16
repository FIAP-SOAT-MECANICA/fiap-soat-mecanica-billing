package br.com.fiap.soat.mecanica.billing.application.orcamento;

/** The budget is not in a state that accepts the requested decision. */
public class TransicaoInvalidaException extends RuntimeException {
    public TransicaoInvalidaException(String message, Throwable cause) { super(message, cause); }
}
