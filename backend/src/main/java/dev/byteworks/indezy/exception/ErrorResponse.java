package dev.byteworks.indezy.exception;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> validationErrors;

    // Custom getters and setters for Map to prevent EI_EXPOSE_REP
    public Map<String, String> getValidationErrors() {
        return validationErrors != null ? new HashMap<>(validationErrors) : new HashMap<>();
    }

    public void setValidationErrors(final Map<String, String> validationErrors) {
        this.validationErrors = validationErrors != null ? new HashMap<>(validationErrors) : new HashMap<>();
    }

    // Custom builder to handle defensive copying
    public static ErrorResponseBuilder builder() {
        return new ErrorResponseBuilder();
    }

    public static class ErrorResponseBuilder {
        private LocalDateTime timestamp;
        private int status;
        private String error;
        private String message;
        private String path;
        private Map<String, String> validationErrors;

        public ErrorResponseBuilder timestamp(final LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public ErrorResponseBuilder status(final int status) {
            this.status = status;
            return this;
        }

        public ErrorResponseBuilder error(final String error) {
            this.error = error;
            return this;
        }

        public ErrorResponseBuilder message(final String message) {
            this.message = message;
            return this;
        }

        public ErrorResponseBuilder path(final String path) {
            this.path = path;
            return this;
        }

        public ErrorResponseBuilder validationErrors(final Map<String, String> validationErrors) {
            this.validationErrors = validationErrors != null ? new HashMap<>(validationErrors) : new HashMap<>();
            return this;
        }

        public ErrorResponse build() {
            ErrorResponse errorResponse = new ErrorResponse();
            errorResponse.timestamp = this.timestamp;
            errorResponse.status = this.status;
            errorResponse.error = this.error;
            errorResponse.message = this.message;
            errorResponse.path = this.path;
            errorResponse.validationErrors = this.validationErrors != null ? new HashMap<>(this.validationErrors) : new HashMap<>();
            return errorResponse;
        }
    }
}
