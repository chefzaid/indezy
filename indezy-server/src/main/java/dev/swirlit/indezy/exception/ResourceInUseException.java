package dev.swirlit.indezy.exception;

/**
 * Raised when a resource cannot be removed because other records still depend on it.
 * Mapped to HTTP 409 so the UI can explain what must be cleaned up first.
 */
public class ResourceInUseException extends RuntimeException {

    public ResourceInUseException(String message) {
        super(message);
    }
}
