package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import br.com.fiap.soat.mecanica.billing.application.Solicitante;
import br.com.fiap.soat.mecanica.billing.application.pagamento.ConsultarPagamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Where the customer finds the link to pay; a customer sees only the charges of their own budgets. */
@RestController
@RequestMapping("/pagamentos")
@Tag(name = "Pagamentos")
class PagamentoController {
    private final ConsultarPagamentoUseCase consultar;

    PagamentoController(ConsultarPagamentoUseCase consultar) { this.consultar = consultar; }

    @GetMapping("/por-os/{osId}")
    @Operation(summary = "Lista as cobranças de uma OS, da mais nova para a mais antiga, com o link de pagamento")
    List<PagamentoResponse> listarPorOs(@PathVariable UUID osId, @AuthenticationPrincipal Jwt jwt) {
        boolean cliente = "CLIENTE".equals(jwt.getClaimAsString("principal_type"));
        return consultar.porOs(osId, new Solicitante(jwt.getSubject(), cliente)).stream()
                .map(PagamentoResponse::de).toList();
    }
}
