package dev.swirlit.indezy.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private UserInfo user;
    /** Set (with a 401) when the password was right but the two-factor code is still needed. */
    private Boolean twoFactorRequired;

    @Data
    public static class UserInfo {
        private Long id;
        private Long accountId;
        private String email;
        private String firstName;
        private String lastName;
    }
}
