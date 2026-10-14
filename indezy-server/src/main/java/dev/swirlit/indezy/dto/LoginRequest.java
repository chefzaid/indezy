package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/** Spring MVC logs request bodies at DEBUG through {@code toString}, so it prints neither the email nor the credentials. */
@Data
public class LoginRequest {
    @ToString.Exclude
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @ToString.Exclude
    @NotBlank(message = "Password is required")
    private String password;

    /** Six-digit authenticator code; required once two-factor authentication is enabled. */
    @ToString.Exclude
    private String totpCode;
}
