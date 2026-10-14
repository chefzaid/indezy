package dev.swirlit.indezy.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A password BCrypt can hash: Spring Security's BCrypt refuses more than {@value #MAX_BYTES} bytes, counted in UTF-8, so
 * an accented letter takes 2 of them and an emoji 4, and a password under 72 characters can still be too long. A null
 * value is valid, so pair it with {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = PasswordMaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordMaxBytes {

    int MAX_BYTES = 72;

    String message() default "Password must be at most " + MAX_BYTES + " bytes in UTF-8 (an accented letter takes 2, an emoji 4)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
