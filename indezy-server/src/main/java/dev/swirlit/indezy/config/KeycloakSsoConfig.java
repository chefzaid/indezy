package dev.swirlit.indezy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class KeycloakSsoConfig {

    @Bean
    public JwtDecoder keycloakJwtDecoder(
            @Value("${indezy.security.keycloak.issuer:https://keycloak.swirlit.dev/auth/realms/swirlit}")
            String issuer,
            @Value("${indezy.security.keycloak.jwk-set-uri:https://keycloak.swirlit.dev/auth/realms/swirlit/protocol/openid-connect/certs}")
            String jwkSetUri,
            @Value("${indezy.security.keycloak.audience:oauth2-proxy}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = token -> token.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required audience is missing", null));
        decoder.setJwtValidator(token -> {
            OAuth2TokenValidatorResult issuerResult = issuerValidator.validate(token);
            return issuerResult.hasErrors() ? issuerResult : audienceValidator.validate(token);
        });
        return decoder;
    }
}
