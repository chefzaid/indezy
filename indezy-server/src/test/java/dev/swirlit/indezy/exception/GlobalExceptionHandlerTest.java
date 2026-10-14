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
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Client errors (4xx) are logged at WARN with only field, constraint and SQL-state names, never the rejected values
 * or database messages; the other Spring MVC request errors keep their status and headers and are logged by type;
 * unexpected server errors stay at ERROR with their stack trace.
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

    @Test
    void methodValidation_ShouldLogParametersAndConstraintsAtWarnWithoutRejectedValues(CapturedOutput output) throws Exception {
        Method method = ContactController.class.getMethod("updateContact", Long.class, ContactDto.class);
        ContactDto contact = new ContactDto();
        contact.setEmail(REJECTED_EMAIL);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(contact, "contactDto");
        bindingResult.addError(fieldError("email", REJECTED_EMAIL, "Email", "Email should be valid"));
        HandlerMethodValidationException ex = new HandlerMethodValidationException(MethodValidationResult.create(contact, method,
            List.of(valueResult(parameter(method, 0), -42L, resolvable("must be greater than 0", "Positive.id", "Positive")),
                new ParameterErrors(parameter(method, 1), contact, bindingResult, null, null, null)),
            List.of(resolvable("The contact does not match the id", "MatchingId"))));

        ResponseEntity<ErrorResponse> response = handler.handleMethodValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Validation Failed");
        assertThat(response.getBody().getValidationErrors())
            .containsEntry("id", "must be greater than 0")
            .containsEntry("email", "Email should be valid")
            .containsEntry("updateContact", "The contact does not match the id");
        assertThat(logLine(output, "Validation failed for"))
            .contains("WARN", "ContactController.updateContact: id (Positive), email (Email), updateContact (MatchingId)")
            .doesNotContain("-42");
        assertThat(output).doesNotContain(REJECTED_EMAIL).doesNotContain("Unexpected error");
    }

    @Test
    void methodValidation_WithoutConstraintCodes_ShouldLogUnknown(CapturedOutput output) throws Exception {
        Method method = ContactController.class.getMethod("updateContact", Long.class, ContactDto.class);
        HandlerMethodValidationException ex = new HandlerMethodValidationException(MethodValidationResult.create(this, method,
            List.of(valueResult(parameter(method, 0), 1L, new DefaultMessageSourceResolvable(null, "Invalid id")),
                valueResult(parameter(method, 1), null, resolvable("Invalid contact")))));

        handler.handleMethodValidation(ex);

        assertThat(logLine(output, "Validation failed for")).contains("WARN", "id (unknown), contactDto (unknown)");
    }

    @Test
    void methodValidation_OfTheReturnValue_ShouldStayAServerFaultAtError(CapturedOutput output) throws Exception {
        Method method = ContactController.class.getMethod("getContactById", Long.class);
        HandlerMethodValidationException ex = new HandlerMethodValidationException(MethodValidationResult.create(this, method,
            List.of(valueResult(new MethodParameter(method, -1), null, resolvable("must not be null", "NotNull")))));
        assertThat(ex.isForReturnValue()).isTrue();

        ResponseEntity<ErrorResponse> response = handler.handleMethodValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(logLine(output, "Unexpected error")).contains("ERROR");
        assertThat(output).contains(HandlerMethodValidationException.class.getName(), "at " + GlobalExceptionHandlerTest.class.getName());
    }

    @Test
    void unsupportedMediaType_ShouldKeep415AndTheAcceptHeaderAndLogOnlyTheType(CapturedOutput output) {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN,
            List.of(MediaType.APPLICATION_JSON), HttpMethod.POST);

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
        assertThat(response.getBody().getStatus()).isEqualTo(415);
        assertThat(response.getBody().getError()).isEqualTo("Unsupported Media Type");
        assertThat(response.getBody().getMessage()).isEqualTo("Content-Type 'text/plain' is not supported.");
        assertThat(logLine(output, "Rejected request")).contains("WARN", "HttpMediaTypeNotSupportedException (415)");
        assertThat(output).doesNotContain("text/plain").doesNotContain("Unexpected error");
    }

    @Test
    void notAcceptable_ShouldKeep406AndTheAcceptHeaderWithoutABody(CapturedOutput output) {
        HttpMediaTypeNotAcceptableException ex = new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorResponse> response = handler.handleNotAcceptable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getHeaders().getAccept()).containsExactly(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNull();
        assertThat(logLine(output, "Rejected request")).contains("WARN", "HttpMediaTypeNotAcceptableException (406)");
        assertThat(output).doesNotContain("Unexpected error");
    }

    @Test
    void missingHeader_ShouldAnswer400AndLogOnlyTheType(CapturedOutput output) throws Exception {
        MissingRequestHeaderException ex = new MissingRequestHeaderException("X-Request-Id", createContactBody());

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError()).isEqualTo("Bad Request");
        assertThat(response.getBody().getMessage()).isEqualTo("Required header 'X-Request-Id' is not present.");
        assertThat(logLine(output, "Rejected request")).contains("WARN", "MissingRequestHeaderException (400)");
        assertThat(output).doesNotContain("X-Request-Id").doesNotContain("Unexpected error");
    }

    @Test
    void requestError_WithANonStandardStatusAndNoDetail_ShouldKeepTheStatusWithAGenericTitle(CapturedOutput output) {
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(new ErrorResponseException(HttpStatusCode.valueOf(499)));

        assertThat(response.getStatusCode().value()).isEqualTo(499);
        assertThat(response.getBody().getError()).isEqualTo("Error");
        assertThat(response.getBody().getMessage()).isEqualTo("Error");
        assertThat(logLine(output, "Rejected request")).contains("WARN", "ErrorResponseException (499)");
    }

    @Test
    void methodNotSupported_ShouldListTheSupportedMethodsInTheAllowHeader(CapturedOutput output) {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE", List.of("GET", "POST"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(response.getBody().getMessage()).isEqualTo("Request method 'DELETE' is not supported");
        assertThat(logLine(output, "Method not supported")).contains("WARN");
    }

    @Test
    void serverSideRequestError_ShouldKeepItsStatusAndStayAtErrorWithStackTrace(CapturedOutput output) {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Mail relay unreachable");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getError()).isEqualTo("Service Unavailable");
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
        assertThat(logLine(output, "Unexpected error")).contains("ERROR", "Mail relay unreachable");
        assertThat(output).contains(ResponseStatusException.class.getName(), "at " + GlobalExceptionHandlerTest.class.getName());
    }

    private static FieldError fieldError(String field, String rejectedValue, String constraint, String message) {
        return new FieldError("contactDto", field, rejectedValue, false, new String[] {constraint}, null, message);
    }

    private static MethodParameter createContactBody() throws NoSuchMethodException {
        return new MethodParameter(ContactController.class.getMethod("createContact", ContactDto.class), 0);
    }

    private static MethodParameter parameter(Method method, int index) {
        MethodParameter parameter = new MethodParameter(method, index);
        parameter.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
        return parameter;
    }

    private static ParameterValidationResult valueResult(MethodParameter parameter, Object argument, MessageSourceResolvable error) {
        return new ParameterValidationResult(parameter, argument, List.of(error), null, null, null, (resolvable, type) -> null);
    }

    /** A constraint error with its message codes, the most specific first, as Spring builds them. */
    private static MessageSourceResolvable resolvable(String message, String... codes) {
        return new DefaultMessageSourceResolvable(codes, message);
    }

    private static String logLine(CapturedOutput output, String marker) {
        return Arrays.stream(output.getAll().split("\\R"))
            .filter(line -> line.contains(marker))
            .findFirst()
            .orElseThrow(() -> new AssertionError("No log line contains: " + marker + "\n" + output.getAll()));
    }
}
