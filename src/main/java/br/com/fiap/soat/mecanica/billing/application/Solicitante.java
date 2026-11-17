package br.com.fiap.soat.mecanica.billing.application;

import br.com.fiap.soat.mecanica.billing.domain.Orcamento;
import java.util.Objects;

/** Who is calling: a customer acts only on their own budgets, staff acts on anyone's behalf. */
public record Solicitante(String id, boolean cliente) {
    public Solicitante {
        Objects.requireNonNull(id, "id");
    }

    public boolean podeAcessar(Orcamento orcamento) {
        return !cliente || orcamento.clienteId().toString().equalsIgnoreCase(id);
    }
}
