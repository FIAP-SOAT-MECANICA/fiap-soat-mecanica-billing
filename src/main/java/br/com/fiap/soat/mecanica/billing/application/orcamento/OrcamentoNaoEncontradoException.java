package br.com.fiap.soat.mecanica.billing.application.orcamento;

import java.util.UUID;

public class OrcamentoNaoEncontradoException extends RuntimeException {
    public OrcamentoNaoEncontradoException(UUID id) { super("Orçamento não encontrado: " + id); }
}
