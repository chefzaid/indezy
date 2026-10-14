package dev.swirlit.indezy.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

/** Counts the bytes as BCrypt does, from the password's UTF-8 encoding. */
public class PasswordMaxBytesValidator implements ConstraintValidator<PasswordMaxBytes, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= PasswordMaxBytes.MAX_BYTES;
    }
}
