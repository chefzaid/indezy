package dev.swirlit.indezy.exception;

/** Raised when an account signs in with the right password before confirming its email. */
public class EmailUnverifiedException extends RuntimeException {

    public EmailUnverifiedException() {
        super("The email of this account is not confirmed yet");
    }
}
