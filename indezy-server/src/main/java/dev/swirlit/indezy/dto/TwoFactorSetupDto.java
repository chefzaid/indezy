package dev.swirlit.indezy.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Data returned when starting two-factor setup: the shared secret and its provisioning URI. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorSetupDto {
    private String secret;
    private String otpauthUri;
}
