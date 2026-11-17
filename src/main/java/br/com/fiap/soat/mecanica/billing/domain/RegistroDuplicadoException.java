package br.com.fiap.soat.mecanica.billing.domain;

/** A unique rule of the store rejected the write; the surrounding transaction must roll back. */
public class RegistroDuplicadoException extends RuntimeException {
    public RegistroDuplicadoException(String message, Throwable cause) { super(message, cause); }
}
