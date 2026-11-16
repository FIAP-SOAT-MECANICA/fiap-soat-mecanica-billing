package br.com.fiap.soat.mecanica.billing.domain;

/** The aggregate changed (or vanished) since it was read; reload and decide again. */
public class ConflitoDeVersaoException extends RuntimeException {
    public ConflitoDeVersaoException(String message) { super(message); }
}
