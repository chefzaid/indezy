package dev.swirlit.indezy.exception;

/**
 * Raised when a registration uses an email that already has an account. A subtype of {@link IllegalArgumentException}
 * so that callers can tell it apart from other invalid input, which is not a conflict.
 */
public class AccountAlreadyExistsException extends IllegalArgumentException {

    public AccountAlreadyExistsException(String message) {
        super(message);
    }
}
