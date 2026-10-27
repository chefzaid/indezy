package dev.swirlit.indezy.mapper;

import dev.swirlit.indezy.dto.*;
import dev.swirlit.indezy.model.SkillDetail;
import dev.swirlit.indezy.model.User;
import org.mapstruct.*;

import java.util.HashMap;
import java.util.Map;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.getFirstName() + \" \" + user.getLastName())")
    @Mapping(target = "avatar", expression = "java(user.getAvatarImage() != null ? user.getAvatarImage() : user.getAvatar())")
    @Mapping(target = "notifications", source = "user")
    @Mapping(target = "preferences", source = "user")
    @Mapping(target = "availabilityDate", ignore = true)
    UserDto toDto(User user);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "lastPasswordChange", ignore = true)
    @Mapping(target = "twoFactorSecret", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "avatarImage", ignore = true)
    @Mapping(target = "onboardingPending", ignore = true)
    User toEntity(UserDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "lastPasswordChange", ignore = true)
    @Mapping(target = "twoFactorSecret", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "email", ignore = true) // Don't allow email updates through profile update
    @Mapping(target = "avatar", ignore = true) // Avatars only change through the upload endpoint
    @Mapping(target = "avatarImage", ignore = true)
    @Mapping(target = "skills", ignore = true)
    @Mapping(target = "skillDetails", ignore = true)
    @Mapping(target = "languages", ignore = true)
    @Mapping(target = "onboardingPending", ignore = true)
    void updateEntity(UserDto dto, @MappingTarget User user);

    /**
     * Sets the profile's lists whole: the entity hands out copies, which a generated update would empty and fill in
     * vain. The skills go first, since only their details are kept.
     */
    @AfterMapping
    default void replaceLists(UserDto dto, @MappingTarget User user) {
        user.setSkills(dto.getSkills());
        user.setLanguages(dto.getLanguages());
        Map<String, SkillDetail> details = new HashMap<>();
        if (dto.getSkillDetails() != null) {
            dto.getSkillDetails().forEach((skill, detail) -> {
                if (detail != null) {
                    details.put(skill, new SkillDetail(detail.level(), detail.years()));
                }
            });
        }
        user.setSkillDetails(details);
    }

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
    @Mapping(target = "inactivityDays", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "autoArchive", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "followUpDays", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "onboardingPending", ignore = true)
    void updatePreferences(UserPreferencesDto dto, @MappingTarget User user);

    // Security settings mapping
    @Mapping(target = "twoFactorEnabled", source = "twoFactorEnabled")
    @Mapping(target = "lastPasswordChange", source = "lastPasswordChange")
    UserSecuritySettingsDto toSecuritySettingsDto(User user);
}
