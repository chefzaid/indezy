package dev.swirlit.indezy.exception;

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

        public ErrorResponseBuilder timestamp(final LocalDateTime value) {
            this.timestamp = value;
            return this;
        }

        public ErrorResponseBuilder status(final int value) {
            this.status = value;
            return this;
        }

        public ErrorResponseBuilder error(final String value) {
            this.error = value;
            return this;
        }

        public ErrorResponseBuilder message(final String value) {
            this.message = value;
            return this;
        }

        public ErrorResponseBuilder path(final String value) {
            this.path = value;
            return this;
        }

        public ErrorResponseBuilder validationErrors(final Map<String, String> value) {
            this.validationErrors = value != null ? new HashMap<>(value) : new HashMap<>();
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
