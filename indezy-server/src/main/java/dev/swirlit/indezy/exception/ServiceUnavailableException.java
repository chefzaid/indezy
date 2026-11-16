package dev.swirlit.indezy.exception;

/** A service the request needs cannot answer right now: answered 503 with the message, fixed text safe to show and log. */
public class ServiceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
