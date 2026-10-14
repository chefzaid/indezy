package dev.swirlit.indezy.validation;

import dev.swirlit.indezy.dto.PasswordChangeRequestDto;
import dev.swirlit.indezy.dto.RegisterRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The limit is BCrypt's, in UTF-8 bytes: an accented letter takes 2 and an emoji 4, so fewer than 72 characters can exceed it. */
class PasswordMaxBytesTest {

    private static final String MESSAGE = "Password must be at most 72 bytes in UTF-8 (an accented letter takes 2, an emoji 4)";
    private static final String ASCII_72 = "a".repeat(72);
    private static final String ACCENTED_72_BYTES = "é".repeat(36);
    private static final String EMOJI_72_BYTES = "🔒".repeat(18);

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void passwordsUpTo72Bytes_ShouldBeValidAndHashable() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        for (String password : List.of(ASCII_72, ACCENTED_72_BYTES, EMOJI_72_BYTES, "a" + "é".repeat(35) + "b")) {
            assertThat(password.getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(72);
            assertThat(validator.validate(register(password))).isEmpty();
            assertThat(validator.validate(passwordChange(password))).isEmpty();
            assertThat(encoder.matches(password, encoder.encode(password))).isTrue();
        }
    }

    @Test
    void passwordsOver72Bytes_ShouldBeRejectedEvenWithFewerThan72Characters() {
        // The values BCrypt itself refuses to hash, which used to reach it and fail the registration with a 409; 37
        // accented letters (74 bytes) and 18 emoji with one letter (73 bytes) are well under 72 characters.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        for (String password : List.of(ASCII_72 + "a", "a".repeat(80), ACCENTED_72_BYTES + "a", "é".repeat(37), EMOJI_72_BYTES + "!")) {
            assertThat(messages(validator.validate(register(password)))).containsExactly("password: " + MESSAGE);
            assertThat(messages(validator.validate(passwordChange(password)))).containsExactly("newPassword: " + MESSAGE);
            assertThatThrownBy(() -> encoder.encode(password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("password cannot be more than 72 bytes");
        }
    }

    @Test
    void nullPassword_ShouldBeLeftToNotBlank() {
        assertThat(new PasswordMaxBytesValidator().isValid(null, null)).isTrue();
        assertThat(messages(validator.validate(register(null)))).containsExactly("password: Password is required");
        assertThat(messages(validator.validate(passwordChange(null))))
            .containsExactly("confirmPassword: Password confirmation is required", "newPassword: New password is required");
    }

    @Test
    void registrationMinimumLength_ShouldBeUnchanged() {
        assertThat(messages(validator.validate(register("abcde")))).containsExactly("password: Password must be at least 6 characters long");
        assertThat(validator.validate(register("abcdef"))).isEmpty();
    }

    @Test
    void passwordChangeMinimumLength_ShouldBe8Characters() {
        // The server enforces the 8-character minimum the profile form asks for, so a request that bypasses the form gets it too.
        String minLengthMessage = "newPassword: New password must be at least 8 characters long";
        for (String password : List.of("a", "abcdefg", "ééééééé")) {
            assertThat(messages(validator.validate(passwordChange(password)))).containsExactly(minLengthMessage);
        }
        for (String password : List.of("abcdefgh", "Password123!", "éééééééé")) {
            assertThat(validator.validate(passwordChange(password))).isEmpty();
        }
        assertThat(messages(validator.validate(passwordChange(""))))
            .containsExactly("confirmPassword: Password confirmation is required", "newPassword: New password is required", minLengthMessage);
    }

    @Test
    void currentPassword_ShouldOnlyBeRequired() {
        // The current password is checked against the stored hash, so an older, shorter one can still be replaced.
        PasswordChangeRequestDto request = passwordChange("Password123!");
        request.setCurrentPassword("abcdef");
        assertThat(validator.validate(request)).isEmpty();

        request.setCurrentPassword(" ");
        assertThat(messages(validator.validate(request))).containsExactly("currentPassword: Current password is required");
    }

    private static RegisterRequest register(String password) {
        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setEmail("jane.doe@example.com");
        request.setPassword(password);
        return request;
    }

    private static PasswordChangeRequestDto passwordChange(String newPassword) {
        PasswordChangeRequestDto request = new PasswordChangeRequestDto();
        request.setCurrentPassword("current-password");
        request.setNewPassword(newPassword);
        request.setConfirmPassword(newPassword);
        return request;
    }

    private static <T> List<String> messages(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath() + ": " + violation.getMessage()).sorted().toList();
    }
}
