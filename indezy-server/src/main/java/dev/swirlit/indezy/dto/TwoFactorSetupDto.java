package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Data returned when starting two-factor setup: the shared secret and its provisioning URI, which embeds the secret and
 * the email. Spring MVC logs response bodies at DEBUG through {@code toString}, so it prints neither.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorSetupDto {
    @ToString.Exclude
    private String secret;
    @ToString.Exclude
    private String otpauthUri;
}
