package br.com.fiap.soat.mecanica.billing.adapters.in.web;

import br.com.fiap.soat.mecanica.billing.application.Solicitante;
import br.com.fiap.soat.mecanica.billing.application.orcamento.AprovarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.ConsultarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.RecusarOrcamentoUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customers see and decide only their own budgets; an ATENDENTE may act on their behalf. */
@RestController
@RequestMapping("/orcamentos")
@Tag(name = "Orçamentos")
class OrcamentoController {
    private static final String CORRELACAO = "X-Correlation-Id";

    private final ConsultarOrcamentoUseCase consultar;
    private final AprovarOrcamentoUseCase aprovar;
    private final RecusarOrcamentoUseCase recusar;

    OrcamentoController(ConsultarOrcamentoUseCase consultar, AprovarOrcamentoUseCase aprovar,
                        RecusarOrcamentoUseCase recusar) {
        this.consultar = consultar;
        this.aprovar = aprovar;
        this.recusar = recusar;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um orçamento pelo id")
    OrcamentoResponse buscar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return OrcamentoResponse.de(consultar.porId(id, solicitante(jwt)));
    }

    @GetMapping("/por-os/{osId}")
    @Operation(summary = "Lista as revisões do orçamento de uma OS, da mais antiga para a mais nova")
    List<OrcamentoResponse> listarPorOs(@PathVariable UUID osId, @AuthenticationPrincipal Jwt jwt) {
        return consultar.porOs(osId, solicitante(jwt)).stream().map(OrcamentoResponse::de).toList();
    }

    @PatchMapping("/{id}/aprovar")
    @Operation(summary = "Aprova um orçamento gerado e ainda não expirado")
    OrcamentoResponse aprovar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                              @RequestHeader(value = CORRELACAO, required = false) String correlationId) {
        return OrcamentoResponse.de(aprovar.executar(id, solicitante(jwt), correlationId));
    }

    @PatchMapping("/{id}/recusar")
    @Operation(summary = "Recusa um orçamento gerado")
    OrcamentoResponse recusar(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt,
                              @RequestHeader(value = CORRELACAO, required = false) String correlationId) {
        return OrcamentoResponse.de(recusar.executar(id, solicitante(jwt), correlationId));
    }

    private static Solicitante solicitante(Jwt jwt) {
        return new Solicitante(jwt.getSubject(), "CLIENTE".equals(jwt.getClaimAsString("principal_type")));
    }
}
