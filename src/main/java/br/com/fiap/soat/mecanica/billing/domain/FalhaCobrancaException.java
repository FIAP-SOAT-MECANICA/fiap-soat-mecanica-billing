package br.com.fiap.soat.mecanica.billing.domain;

/** The payment provider could not be reached or rejected the call; the caller may retry later. */
public class FalhaCobrancaException extends RuntimeException {
    public FalhaCobrancaException(String mensagem, Throwable causa) { super(mensagem, causa); }

    public FalhaCobrancaException(String mensagem) { super(mensagem); }
}
