package dev.swirlit.indezy.dto;

import dev.swirlit.indezy.validation.PasswordMaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

/** Spring MVC logs request bodies at DEBUG through {@code toString}, so it prints neither the identity nor the password. */
@Data
public class RegisterRequest {
    @ToString.Exclude
    @NotBlank(message = "First name is required")
    private String firstName;

    @ToString.Exclude
    @NotBlank(message = "Last name is required")
    private String lastName;

    @ToString.Exclude
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    @ToString.Exclude
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters long")
    @PasswordMaxBytes
    private String password;
}
