package br.com.fiap.soat.mecanica.billing.application;

/** The caller is authenticated but the resource belongs to someone else. */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String message) { super(message); }
}
