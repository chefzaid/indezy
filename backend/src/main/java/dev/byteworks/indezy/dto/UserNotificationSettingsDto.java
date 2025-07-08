package dev.byteworks.indezy.dto;

import lombok.Data;

@Data
public class UserNotificationSettingsDto {
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean projectUpdates;
    private Boolean clientMessages;
    private Boolean systemAlerts;
    private Boolean weeklyReports;
    private Boolean marketingEmails;
}
