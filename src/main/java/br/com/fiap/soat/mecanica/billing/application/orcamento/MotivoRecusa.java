package br.com.fiap.soat.mecanica.billing.application.orcamento;

/**
 * Why a budget is announced as OrcamentoRecusado. The Saga reacts the same way to both; the reason travels in the
 * payload so the OS can tell them apart in its history.
 */
public enum MotivoRecusa {
    RECUSADO_PELO_CLIENTE,
    /** Nobody decided before the validity ended. */
    EXPIRADO
}
