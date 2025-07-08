package dev.byteworks.indezy.mapper;

import dev.byteworks.indezy.dto.*;
import dev.byteworks.indezy.model.User;
import dev.byteworks.indezy.model.UserSecurityQuestion;
import dev.byteworks.indezy.model.UserSession;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
    @Mapping(target = "notifications", source = "user")
    @Mapping(target = "preferences", source = "user")
    UserDto toDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "lastPasswordChange", ignore = true)
    @Mapping(target = "sessions", ignore = true)
    @Mapping(target = "securityQuestions", ignore = true)
    @Mapping(target = "twoFactorSecret", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    User toEntity(UserDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "lastPasswordChange", ignore = true)
    @Mapping(target = "sessions", ignore = true)
    @Mapping(target = "securityQuestions", ignore = true)
    @Mapping(target = "twoFactorSecret", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "email", ignore = true) // Don't allow email updates through profile update
    void updateEntity(UserDto dto, @MappingTarget User user);

    // Notification settings mapping
    @Mapping(target = "emailNotifications", source = "emailNotifications")
    @Mapping(target = "pushNotifications", source = "pushNotifications")
    @Mapping(target = "projectUpdates", source = "projectUpdates")
    @Mapping(target = "clientMessages", source = "clientMessages")
    @Mapping(target = "systemAlerts", source = "systemAlerts")
    @Mapping(target = "weeklyReports", source = "weeklyReports")
    @Mapping(target = "marketingEmails", source = "marketingEmails")
    UserNotificationSettingsDto toNotificationSettingsDto(User user);

    void updateNotificationSettings(UserNotificationSettingsDto dto, @MappingTarget User user);

    // Preferences mapping
    @Mapping(target = "theme", source = "theme")
    @Mapping(target = "language", source = "languagePreference")
    @Mapping(target = "dateFormat", source = "dateFormat")
    @Mapping(target = "timeFormat", source = "timeFormat")
    @Mapping(target = "currency", source = "currency")
    @Mapping(target = "timezone", source = "timezone")
    @Mapping(target = "defaultView", source = "defaultView")
    @Mapping(target = "itemsPerPage", source = "itemsPerPage")
    @Mapping(target = "autoSave", source = "autoSave")
    UserPreferencesDto toPreferencesDto(User user);

    @Mapping(target = "languagePreference", source = "language")
    void updatePreferences(UserPreferencesDto dto, @MappingTarget User user);

    // Security settings mapping
    @Mapping(target = "twoFactorEnabled", source = "twoFactorEnabled")
    @Mapping(target = "lastPasswordChange", source = "lastPasswordChange")
    @Mapping(target = "loginSessions", source = "sessions")
    @Mapping(target = "securityQuestions", source = "securityQuestions")
    UserSecuritySettingsDto toSecuritySettingsDto(User user);

    // Session mapping
    @Mapping(target = "id", source = "sessionId")
    @Mapping(target = "current", source = "isCurrent")
    UserSessionDto toSessionDto(UserSession session);

    // Security question mapping
    @Mapping(target = "answer", ignore = true) // Never expose the answer
    UserSecurityQuestionDto toSecurityQuestionDto(UserSecurityQuestion question);
}
