package dev.swirlit.indezy.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maps exceptions to error responses. Client errors (4xx) are logged at WARN. Validation, binding, parser, database and
 * {@link IllegalArgumentException} messages can quote what the user sent (emails, phone numbers, passwords), so they are
 * never logged: the log names only the fields, constraints, parameters or SQL state involved. The other Spring MVC
 * request errors keep their own status and headers. Unexpected server errors stay at ERROR with their stack trace.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String UNEXPECTED_ERROR = "An unexpected error occurred";

    /** A rejected method parameter, field of a parameter, or combination of parameters, with its constraint. */
    private record Violation(String name, MessageSourceResolvable error) {
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        // Built by the application from ids and fixed text only, so the message is safe to log.
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage());
    }

    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<ErrorResponse> handleResourceInUse(ResourceInUseException ex) {
        log.warn("Resource in use: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidStateException ex) {
        log.warn("Invalid state: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        // The message can quote user input (e.g. "Email already exists: ..."), so only where it was thrown is logged.
        log.warn("Illegal argument rejected at {}", ExceptionLogDetails.originOf(ex));
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        List<ObjectError> bindingErrors = ex.getBindingResult().getAllErrors();
        log.warn("Validation failed for {}: {}", ex.getObjectName(), bindingErrors.stream()
            .map(error -> fieldOf(error) + " (" + error.getCode() + ")")
            .collect(Collectors.joining(", ")));

        Map<String, String> errors = new HashMap<>();
        bindingErrors.forEach(error -> errors.put(fieldOf(error), error.getDefaultMessage()));
        return validationFailed(errors);
    }

    /** Constraints declared on controller method parameters, such as {@code @RequestParam @Min(1) int page}. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        if (ex.isForReturnValue()) {
            // The controller returned an invalid value: a server fault, which keeps its 500 status.
            return handleGenericException(ex);
        }
        Method method = ex.getMethod();
        List<Violation> violations = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                violations.add(new Violation(error instanceof FieldError fieldError ? fieldError.getField() : parameter, error)));
        }
        // A constraint on several parameters together is reported under the method name.
        ex.getCrossParameterValidationResults().forEach(error -> violations.add(new Violation(method.getName(), error)));
        log.warn("Validation failed for {}.{}: {}", method.getDeclaringClass().getSimpleName(), method.getName(), violations.stream()
            .map(violation -> violation.name() + " (" + codeOf(violation.error()) + ")")
            .collect(Collectors.joining(", ")));

        Map<String, String> errors = new HashMap<>();
        violations.forEach(violation -> errors.put(violation.name(), violation.error().getDefaultMessage()));
        return validationFailed(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        log.warn("Constraint violation on {}", describeViolations(ex));
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Failed", "Invalid input data");
    }

    /** Malformed JSON bodies, wrong parameter types and missing parameters are client errors, not server faults. */
    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        log.warn("Rejected malformed request: {}", describeBadRequest(ex));
        return buildResponse(HttpStatus.BAD_REQUEST, "Bad Request", "The request is malformed or has invalid parameters");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONTENT_TOO_LARGE, "Payload Too Large", "The uploaded file is too large");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ExceptionLogDetails.describe(ex));
        return buildResponse(HttpStatus.CONFLICT, "Conflict",
            "The operation conflicts with existing data (missing or still-referenced record)");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", "Access denied to resource");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No endpoint for {}", ex.getResourcePath());
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", "No endpoint matches this request");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        // The Allow header lists the supported methods.
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getHeaders(), "Method Not Allowed", ex.getMessage());
    }

    /** The client accepts none of the types the endpoint produces, JSON included, so a JSON error body could not be written either. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        logRejectedRequest(ex, ex.getStatusCode());
        return new ResponseEntity<>(ex.getHeaders(), ex.getStatusCode());
    }

    /**
     * Any other exception is a server fault, except the Spring MVC request errors without a dedicated handler
     * (unsupported media type, missing header, ...), which keep their status and headers, such as {@code Accept}.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse requestError) {
            return handleRequestError(ex, requestError);
        }
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", UNEXPECTED_ERROR);
    }

    /** A client error answers Spring's description of the problem; a server error is logged like any unexpected one. */
    private static ResponseEntity<ErrorResponse> handleRequestError(Exception ex, org.springframework.web.ErrorResponse requestError) {
        HttpStatusCode status = requestError.getStatusCode();
        ProblemDetail problem = requestError.getBody();
        String error = Objects.requireNonNullElse(problem.getTitle(), "Error");
        if (status.is4xxClientError()) {
            logRejectedRequest(ex, status);
            return buildResponse(status, requestError.getHeaders(), error, Objects.requireNonNullElse(problem.getDetail(), error));
        }
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(status, requestError.getHeaders(), error, UNEXPECTED_ERROR);
    }

    /** Only the type and status: the message can quote request values, such as the rejected {@code Content-Type}. */
    private static void logRejectedRequest(Exception ex, HttpStatusCode status) {
        log.warn("Rejected request: {} ({})", ex.getClass().getSimpleName(), status.value());
    }

    private static String fieldOf(ObjectError error) {
        return error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
    }

    /** The constraint name, the most generic of the message codes (such as {@code Min}), never the rejected value. */
    private static String codeOf(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? ExceptionLogDetails.UNKNOWN : codes[codes.length - 1];
    }

    /** Property paths with their constraint names, sorted so the log line is stable. */
    private static String describeViolations(ConstraintViolationException ex) {
        if (ex.getConstraintViolations() == null) {
            return ExceptionLogDetails.UNKNOWN;
        }
        return ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + " (" +
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName() + ")")
            .sorted()
            .collect(Collectors.joining(", "));
    }

    /** Names the offending parameter or part instead of quoting the rejected value or body. */
    private static String describeBadRequest(Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return "parameter '" + mismatch.getName() + "' has an invalid value";
        }
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "parameter '" + missing.getParameterName() + "' is missing";
        }
        if (ex instanceof MissingServletRequestPartException missing) {
            return "part '" + missing.getRequestPartName() + "' is missing";
        }
        return "request body is not readable";
    }

    private static ResponseEntity<ErrorResponse> validationFailed(Map<String, String> errors) {
        if (!acceptsJson()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now(ZoneId.systemDefault()))
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid input data")
            .validationErrors(errors)
            .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    private static ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        return buildResponse(status, HttpHeaders.EMPTY, error, message);
    }

    private static ResponseEntity<ErrorResponse> buildResponse(HttpStatusCode status, HttpHeaders headers, String error, String message) {
        if (!acceptsJson()) {
            return new ResponseEntity<>(headers, status);
        }
        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now(ZoneId.systemDefault()))
            .status(status.value())
            .error(error)
            .message(message)
            .build();
        return new ResponseEntity<>(errorResponse, headers, status);
    }

    /**
     * Whether the request takes the JSON error body, as one without an {@code Accept} header does. A client that accepts
     * no JSON gets the status alone: a body it refuses cannot be written, and Spring would then answer through its own
     * resolver, which logs the exception's message, rejected values included, at WARN.
     */
    private static boolean acceptsJson() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return true;
        }
        String accept = attributes.getRequest().getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                .anyMatch(type -> type.getQualityValue() > 0 && type.isCompatibleWith(MediaType.APPLICATION_JSON));
        } catch (InvalidMediaTypeException e) {
            return true;
        }
    }
}
