package br.com.fiap.soat.mecanica.billing.application.outbox;

/** Sends one outbox event to the message broker. */
public interface PublicadorEventos {
    /**
     * @param tipo     event type, also sent as a message attribute so subscribers can filter on it
     * @param envelope the message envelope, as the JSON document stored in the outbox
     * @throws FalhaPublicacaoException if the broker did not accept the message
     */
    void publicar(String tipo, String envelope);
}
