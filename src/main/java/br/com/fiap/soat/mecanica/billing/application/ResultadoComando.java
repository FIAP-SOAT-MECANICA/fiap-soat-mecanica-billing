package br.com.fiap.soat.mecanica.billing.application;

/** Outcome of a command taken from the queue; both values mean the message can be deleted. */
public enum ResultadoComando {
    /** The command took effect, or was a harmless no-op (for example cancelling something already final). */
    PROCESSADO,
    /** The message id was already processed. */
    DUPLICADO
}
