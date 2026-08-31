package dev.swirlit.indezy.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private UserInfo user;

    @Data
    public static class UserInfo {
        private Long id;
        private Long accountId;
        private String email;
        private String firstName;
        private String lastName;
    }
}
