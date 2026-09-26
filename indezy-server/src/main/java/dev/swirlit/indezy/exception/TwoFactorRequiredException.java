package dev.swirlit.indezy.exception;

/** The password is correct but the account also requires its two-factor (TOTP) code. */
public class TwoFactorRequiredException extends RuntimeException {

    public TwoFactorRequiredException() {
        super("Two-factor code required");
    }
}
