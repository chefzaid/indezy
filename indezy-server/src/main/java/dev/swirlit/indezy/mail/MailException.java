package dev.swirlit.indezy.mail;

/** Resend did not accept an email: unreachable, refused or an unexpected answer. */
public class MailException extends RuntimeException {

    public MailException(String message) {
        super(message);
    }

    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}
