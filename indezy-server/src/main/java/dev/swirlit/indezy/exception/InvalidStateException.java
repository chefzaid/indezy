package dev.swirlit.indezy.exception;

/**
 * Raised when a request does not fit the current state of a resource, such as editing an activity report already
 * submitted for validation. Mapped to HTTP 409; the message is fixed text, safe to show and to log.
 */
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }
}
