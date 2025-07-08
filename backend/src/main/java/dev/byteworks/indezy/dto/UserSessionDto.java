package dev.byteworks.indezy.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserSessionDto {
    private String id;
    private String device;
    private String browser;
    private String location;
    private LocalDateTime lastActive;
    private Boolean current;
}
