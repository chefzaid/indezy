package dev.swirlit.indezy.dto;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDate;

/** Spring MVC logs response bodies at DEBUG through {@code toString}, so it prints neither the token nor who signed in. */
@Data
public class LoginResponse {
    @ToString.Exclude
    private String token;
    private UserInfo user;
    /** Set (with a 401) when the password was right but the two-factor code is still needed. */
    private Boolean twoFactorRequired;
    /** The account is on hold before its deletion: signing in again with {@code restore} takes it off hold. */
    private Boolean accountOnHold;
    /** The day an account on hold is deleted for good. */
    private LocalDate purgeOn;

    @Data
    public static class UserInfo {
        private Long id;
        private Long accountId;
        @ToString.Exclude
        private String email;
        @ToString.Exclude
        private String firstName;
        @ToString.Exclude
        private String lastName;
        /** A new account that has not finished or skipped its welcome steps yet. */
        private boolean onboardingPending;
    }
}
