package br.com.fiap.soat.mecanica.billing.config;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTest {
    @Test void semChavePublicaTodoTokenERecusado() {
        var decoder = new SecurityConfig().jwtDecoder("  ", "emissor", "audiencia");
        assertThrows(BadJwtException.class, () -> decoder.decode("qualquer"));
    }

    @Test void leChaveEmPemComOuSemQuebrasDeLinha() throws Exception {
        var gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        var chave = gerador.generateKeyPair().getPublic();
        String base64 = Base64.getEncoder().encodeToString(chave.getEncoded());
        assertEquals(chave, SecurityConfig.lerChave(base64));
        assertEquals(chave, SecurityConfig.lerChave(
                "-----BEGIN PUBLIC KEY-----\\n" + base64 + "\\n-----END PUBLIC KEY-----\n"));
        assertThrows(IllegalStateException.class, () -> SecurityConfig.lerChave("nao e chave"));
    }

    @Test void papelVemDoTipoEDoCargo() {
        assertEquals("[ROLE_CLIENTE]", papeis(Map.of("principal_type", "CLIENTE", "cargo", "ATENDENTE")));
        assertEquals("[ROLE_ATENDENTE]", papeis(Map.of("principal_type", "FUNCIONARIO", "cargo", "ATENDENTE")));
        assertEquals("[]", papeis(Map.of("principal_type", "FUNCIONARIO")));
        assertEquals("[]", papeis(Map.of("principal_type", "FUNCIONARIO", "cargo", "atendente; x")));
        assertEquals("[]", papeis(Map.of("cargo", "ATENDENTE")));
    }

    private static String papeis(Map<String, Object> claims) {
        var jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("s")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(claims)).build();
        return SecurityConfig.papeis(jwt).stream().map(GrantedAuthority::getAuthority).toList().toString();
    }
}
