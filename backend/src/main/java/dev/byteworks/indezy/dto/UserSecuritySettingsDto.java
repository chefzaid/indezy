package dev.byteworks.indezy.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserSecuritySettingsDto {
    private Boolean twoFactorEnabled;
    private LocalDateTime lastPasswordChange;
    private List<UserSessionDto> loginSessions;
    private List<UserSecurityQuestionDto> securityQuestions;
}
