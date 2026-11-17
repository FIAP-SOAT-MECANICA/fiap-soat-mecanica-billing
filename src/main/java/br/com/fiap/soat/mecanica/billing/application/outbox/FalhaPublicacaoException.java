package br.com.fiap.soat.mecanica.billing.application.outbox;

/** The broker did not accept the event; it stays in the outbox for the next attempt. */
public class FalhaPublicacaoException extends RuntimeException {
    public FalhaPublicacaoException(String message, Throwable cause) { super(message, cause); }
}
