package dev.swirlit.indezy.exception;

import dev.swirlit.indezy.controller.ContactController;
import dev.swirlit.indezy.dto.ContactDto;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.sql.SQLException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client errors (4xx) are logged at WARN with only field, constraint and SQL-state names, never the rejected values
 * or database messages; unexpected server errors stay at ERROR with their stack trace.
 */
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    private static final String REJECTED_EMAIL = "jane.private@mail.example";
    private static final String REJECTED_PHONE = "+33 6 12 34 56 78";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationError_ShouldLogFieldsAndConstraintsAtWarnWithoutRejectedValues(CapturedOutput output) throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new ContactDto(), "contactDto");
        bindingResult.addError(fieldError("email", REJECTED_EMAIL, "Email", "Email should be valid"));
        bindingResult.addError(fieldError("phone", REJECTED_PHONE, "Pattern", "Phone number is invalid"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(createContactBody(), bindingResult);
        assertThat(ex.getMessage()).contains(REJECTED_EMAIL, REJECTED_PHONE);

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getValidationErrors())
            .containsEntry("email", "Email should be valid")
            .containsEntry("phone", "Phone number is invalid");
        assertThat(logLine(output, "Validation failed for contactDto"))
            .contains("WARN", "email (Email)", "phone (Pattern)")
            .doesNotContain("ERROR");
        assertThat(output).doesNotContain(REJECTED_EMAIL).doesNotContain(REJECTED_PHONE);
    }

    @Test
    void validationError_WithObjectLevelError_ShouldReportItUnderTheObjectName(CapturedOutput output) throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new ContactDto(), "contactDto");
        bindingResult.addError(new ObjectError("contactDto", new String[] {"ContactReachable"}, null, "Email or phone is required"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(createContactBody(), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex);

        assertThat(response.getBody().getValidationErrors()).containsEntry("contactDto", "Email or phone is required");
        assertThat(logLine(output, "Validation failed for contactDto")).contains("WARN", "contactDto (ContactReachable)");
    }

    @Test
    void constraintViolation_ShouldLogPathsAndConstraintsAtWarnWithoutRejectedValues(CapturedOutput output) {
        ContactDto contact = new ContactDto();
        contact.setEmail(REJECTED_EMAIL.replace("@", " at "));
        ConstraintViolationException ex;
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ex = new ConstraintViolationException(validator.validate(contact));
        }

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(logLine(output, "Constraint violation"))
            .contains("WARN", "email (Email)", "firstName (NotBlank)")
            .doesNotContain("ERROR");
        assertThat(output).doesNotContain(contact.getEmail());
    }

    @Test
    void constraintViolation_WithoutViolations_ShouldLogUnknown(CapturedOutput output) {
        handler.handleConstraintViolation(new ConstraintViolationException("Invalid value " + REJECTED_EMAIL, null));

        assertThat(logLine(output, "Constraint violation")).contains("WARN", "on unknown");
        assertThat(output).doesNotContain(REJECTED_EMAIL);
    }

    @Test
    void dataIntegrityViolation_ShouldLogConstraintAndSqlStateWithoutDatabaseMessage(CapturedOutput output) {
        String databaseMessage = "ERROR: duplicate key value violates unique constraint \"uk_freelance_email\" " +
            "Detail: Key (email)=(" + REJECTED_EMAIL + ") already exists.";
        SQLException sqlException = new SQLException(databaseMessage, "23505");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement",
            new org.hibernate.exception.ConstraintViolationException(databaseMessage, sqlException, "uk_freelance_email"));

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(logLine(output, "Data integrity violation"))
            .contains("WARN", "constraint uk_freelance_email", "SQL state 23505")
            .doesNotContain("ERROR");
        assertThat(output).doesNotContain(REJECTED_EMAIL).doesNotContain("duplicate key");
    }

    @Test
    void dataIntegrityViolation_WithoutDatabaseDetails_ShouldLogUnknownConstraintAndSqlState(CapturedOutput output) {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
            "not-null property references a null or transient value: " + REJECTED_EMAIL);

        handler.handleDataIntegrityViolation(ex);

        assertThat(logLine(output, "Data integrity violation")).contains("WARN", "constraint unknown", "SQL state unknown");
        assertThat(output).doesNotContain(REJECTED_EMAIL);
    }

    @Test
    void illegalArgument_ShouldLogItsOriginAtWarnWithoutTheMessage(CapturedOutput output) {
        IllegalArgumentException ex = new IllegalArgumentException("Email already exists: " + REJECTED_EMAIL);

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgumentException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).isEqualTo(ex.getMessage());
        assertThat(logLine(output, "Illegal argument rejected"))
            .contains("WARN", GlobalExceptionHandlerTest.class.getName() + ".illegalArgument_ShouldLogItsOriginAtWarnWithoutTheMessage")
            .doesNotContain("ERROR");
        assertThat(output).doesNotContain(REJECTED_EMAIL);
    }

    @Test
    void illegalArgument_WithoutStackTrace_ShouldLogUnknownOrigin(CapturedOutput output) {
        IllegalArgumentException ex = new IllegalArgumentException("Email already exists: " + REJECTED_EMAIL);
        ex.setStackTrace(new StackTraceElement[0]);

        handler.handleIllegalArgumentException(ex);

        assertThat(logLine(output, "Illegal argument rejected")).contains("WARN", "at unknown");
        assertThat(output).doesNotContain(REJECTED_EMAIL);
    }

    @Test
    void resourceNotFound_ShouldLogAtWarn(CapturedOutput output) {
        handler.handleResourceNotFoundException(new ResourceNotFoundException("Contact not found with id: 42"));

        assertThat(logLine(output, "Resource not found")).contains("WARN", "Contact not found with id: 42").doesNotContain("ERROR");
    }

    @Test
    void parameterTypeMismatch_ShouldLogTheParameterNameWithoutTheValue(CapturedOutput output) throws Exception {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(REJECTED_EMAIL, Long.class, "id",
            createContactBody(), new NumberFormatException("For input string: \"" + REJECTED_EMAIL + "\""));
        assertThat(ex.getMessage()).contains(REJECTED_EMAIL);

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(logLine(output, "Rejected malformed request")).contains("WARN", "parameter 'id' has an invalid value");
        assertThat(output).doesNotContain(REJECTED_EMAIL);
    }

    @Test
    void missingParameter_ShouldLogTheParameterName(CapturedOutput output) {
        handler.handleBadRequest(new MissingServletRequestParameterException("year", "Integer"));

        assertThat(logLine(output, "Rejected malformed request")).contains("WARN", "parameter 'year' is missing");
    }

    @Test
    void missingPart_ShouldLogThePartName(CapturedOutput output) {
        handler.handleBadRequest(new MissingServletRequestPartException("file"));

        assertThat(logLine(output, "Rejected malformed request")).contains("WARN", "part 'file' is missing");
    }

    @Test
    void unreadableBody_ShouldLogWithoutTheParserMessage(CapturedOutput output) {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
            "JSON parse error: Cannot deserialize value of type `java.lang.Long` from String \"" + REJECTED_EMAIL + "\"",
            new MockHttpInputMessage(new byte[0]));

        handler.handleBadRequest(ex);

        assertThat(logLine(output, "Rejected malformed request")).contains("WARN", "request body is not readable");
        assertThat(output).doesNotContain(REJECTED_EMAIL);
    }

    @Test
    void unexpectedError_ShouldStayAtErrorWithStackTrace(CapturedOutput output) {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(new IllegalStateException("Mapper failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(logLine(output, "Unexpected error")).contains("ERROR", "Mapper failed");
        assertThat(output).contains("java.lang.IllegalStateException: Mapper failed", "at " + GlobalExceptionHandlerTest.class.getName());
    }

    private static FieldError fieldError(String field, String rejectedValue, String constraint, String message) {
        return new FieldError("contactDto", field, rejectedValue, false, new String[] {constraint}, null, message);
    }

    private static MethodParameter createContactBody() throws NoSuchMethodException {
        return new MethodParameter(ContactController.class.getMethod("createContact", ContactDto.class), 0);
    }

    private static String logLine(CapturedOutput output, String marker) {
        return Arrays.stream(output.getAll().split("\\R"))
            .filter(line -> line.contains(marker))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No log line contains: " + marker + "\n" + output.getAll()));
    }
}
