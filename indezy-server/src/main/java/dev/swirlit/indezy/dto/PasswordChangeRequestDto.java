package dev.swirlit.indezy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

/** Spring MVC logs request bodies at DEBUG through {@code toString}, so it prints none of the passwords. */
@Data
public class PasswordChangeRequestDto {
    @ToString.Exclude
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @ToString.Exclude
    @NotBlank(message = "New password is required")
    private String newPassword;

    @ToString.Exclude
    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
