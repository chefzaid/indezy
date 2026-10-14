package dev.swirlit.indezy.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Maps exceptions to error responses. Client errors (4xx) are logged at WARN. Validation, binding, parser, database and
 * {@link IllegalArgumentException} messages can quote what the user sent (emails, phone numbers, passwords), so they are
 * never logged: the log names only the fields, constraints, parameters or SQL state involved. Unexpected server errors
 * stay at ERROR with their stack trace.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String UNKNOWN = "unknown";

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        // The message can quote user input (e.g. "Email already exists: ..."), so only where it was thrown is logged.
        log.warn("Illegal argument rejected at {}", originOf(ex));
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

        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now(ZoneId.systemDefault()))
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .message("Invalid input data")
            .validationErrors(errors)
            .build();

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
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
        String constraint = ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException violation ?
            violation.getConstraintName() : null;
        String sqlState = ex.getMostSpecificCause() instanceof SQLException sqlException ? sqlException.getSQLState() : null;
        log.warn("Data integrity violation: constraint {}, SQL state {}",
            Objects.requireNonNullElse(constraint, UNKNOWN), Objects.requireNonNullElse(sqlState, UNKNOWN));
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
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "Method Not Allowed", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred");
    }

    private static String fieldOf(ObjectError error) {
        return error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
    }

    /** Property paths with their constraint names, sorted so the log line is stable. */
    private static String describeViolations(ConstraintViolationException ex) {
        if (ex.getConstraintViolations() == null) {
            return UNKNOWN;
        }
        return ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + " (" +
                violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName() + ")")
            .sorted()
            .collect(Collectors.joining(", "));
    }

    private static String originOf(Throwable ex) {
        StackTraceElement[] trace = ex.getStackTrace();
        if (trace.length == 0) {
            return UNKNOWN;
        }
        return trace[0].getClassName() + "." + trace[0].getMethodName() + ":" + trace[0].getLineNumber();
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

    private static ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String error, String message) {
        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now(ZoneId.systemDefault()))
            .status(status.value())
            .error(error)
            .message(message)
            .build();
        return new ResponseEntity<>(errorResponse, status);
    }
}
