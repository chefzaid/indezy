package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/**
 * Authenticator code that activates or disables two-factor authentication. Spring MVC logs request bodies at DEBUG
 * through {@code toString}, so it does not print the code.
 */
@Data
public class TwoFactorCodeRequest {
    /** Only a missing or blank code is a validation error; a malformed one is answered as a wrong code, as the profile page expects. */
    @ToString.Exclude
    @NotBlank(message = "Verification code is required")
    private String code;
}
