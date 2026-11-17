package dev.swirlit.indezy.exception;

import java.time.LocalDate;

/** The account signing in is on hold before its deletion: told (after its credentials were checked) with the day it goes. */
public class AccountOnHoldException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final LocalDate purgeOn;

    public AccountOnHoldException(LocalDate purgeOn) {
        super("The account is on hold until its deletion");
        this.purgeOn = purgeOn;
    }

    public LocalDate getPurgeOn() {
        return purgeOn;
    }
}
