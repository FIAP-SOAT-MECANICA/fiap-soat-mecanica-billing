package br.com.fiap.soat.mecanica.billing.config;

import jakarta.servlet.DispatcherType;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The service validates the Identity JWT itself, even behind the API Gateway authorizer, so the
 * public load balancer cannot be used to bypass authentication. Absent when the application runs
 * without a web server, as the persistence tests do.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
    static final String TIPO = "principal_type";

    @Bean
    SecurityFilterChain filtros(HttpSecurity http) throws Exception {
        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(SecurityConfig::papeis);
        return http
                .csrf(csrf -> csrf.disable()) // Stateless API authenticated by bearer token only.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/orcamentos/**").hasAnyRole("CLIENTE", "ATENDENTE")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conversor)))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${billing.jwt.public-key}") String chavePublica,
                          @Value("${billing.jwt.issuer}") String emissor,
                          @Value("${billing.jwt.audience}") String audiencia) {
        if (chavePublica.isBlank()) {
            log.warn("BILLING_JWT_PUBLIC_KEY ausente: toda requisicao autenticada sera recusada");
            return token -> { throw new BadJwtException("chave publica do emissor nao configurada"); };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(lerChave(chavePublica))
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(emissor),
                new JwtClaimValidator<Instant>("exp", Objects::nonNull),
                new JwtClaimValidator<String>("sub", sub -> sub != null && !sub.isBlank()),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audiencia))));
        return decoder;
    }

    /** CLIENTE for customers; staff gets the role named by the {@code cargo} claim. */
    static Collection<GrantedAuthority> papeis(Jwt jwt) {
        String tipo = jwt.getClaimAsString(TIPO);
        if ("CLIENTE".equals(tipo)) return List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"));
        String cargo = jwt.getClaimAsString("cargo");
        if ("FUNCIONARIO".equals(tipo) && cargo != null && cargo.matches("[A-Z_]+"))
            return List.of(new SimpleGrantedAuthority("ROLE_" + cargo));
        return List.of();
    }

    /** Accepts the PEM with real line breaks, escaped ones or none, as secrets often arrive. */
    static RSAPublicKey lerChave(String pem) {
        String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replace("\\n", "").replaceAll("\\s", "");
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalStateException("BILLING_JWT_PUBLIC_KEY nao e uma chave publica RSA em PEM", e);
        }
    }
}
