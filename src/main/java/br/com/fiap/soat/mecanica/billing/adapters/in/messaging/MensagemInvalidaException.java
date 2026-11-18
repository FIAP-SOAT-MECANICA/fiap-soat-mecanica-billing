package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

/** The message cannot be understood; redelivering it will not help, so it ends up in the dead-letter queue. */
class MensagemInvalidaException extends RuntimeException {
    MensagemInvalidaException(String mensagem) { super(mensagem); }

    MensagemInvalidaException(String mensagem, Throwable causa) { super(mensagem, causa); }
}
