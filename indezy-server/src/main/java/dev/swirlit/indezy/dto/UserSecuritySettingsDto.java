package dev.swirlit.indezy.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSecuritySettingsDto {
    private Boolean twoFactorEnabled;
    private LocalDateTime lastPasswordChange;
}
