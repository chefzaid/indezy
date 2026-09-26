package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.*;
import dev.swirlit.indezy.service.UserDataExportService;
import dev.swirlit.indezy.service.UserService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/** The signed-in account: profile, preferences, notifications, security and data export. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "Profile, preferences and security of the signed-in account")
public class UserController {

    private final UserService userService;
    private final UserDataExportService userDataExportService;

    @Operation(summary = "Get the profile")
    @GetMapping("/profile")
    public UserDto getUserProfile() {
        return userService.getUserProfile(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Update the profile")
    @PutMapping("/profile")
    public UserDto updateUserProfile(@Valid @RequestBody UserDto userDto) {
        return userService.updateUserProfile(SecurityUtils.getCurrentUserId(), userDto);
    }

    @Operation(summary = "Upload an avatar", description = "Stored as a data URL (PNG, JPEG, WebP or GIF, 512 KB max)")
    @PostMapping("/avatar")
    public ResponseEntity<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(userService.uploadAvatar(SecurityUtils.getCurrentUserId(), file));
        } catch (IOException e) {
            log.error("Error uploading avatar", e);
            return ResponseEntity.badRequest().body("Error uploading file");
        }
    }

    @Operation(summary = "Change the password", description = "400 when the current password is wrong")
    @PostMapping("/change-password")
    public boolean changePassword(@Valid @RequestBody PasswordChangeRequestDto request) {
        return userService.changePassword(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Get the preferences")
    @GetMapping("/preferences")
    public UserPreferencesDto getUserPreferences() {
        return userService.getUserPreferences(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Update the preferences")
    @PutMapping("/preferences")
    public UserPreferencesDto updateUserPreferences(@Valid @RequestBody UserPreferencesDto preferencesDto) {
        return userService.updateUserPreferences(SecurityUtils.getCurrentUserId(), preferencesDto);
    }

    @Operation(summary = "Get the notification settings")
    @GetMapping("/notifications")
    public UserNotificationSettingsDto getNotificationSettings() {
        return userService.getNotificationSettings(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Update the notification settings")
    @PutMapping("/notifications")
    public UserNotificationSettingsDto updateNotificationSettings(
            @Valid @RequestBody UserNotificationSettingsDto settingsDto) {
        return userService.updateNotificationSettings(SecurityUtils.getCurrentUserId(), settingsDto);
    }

    @Operation(summary = "Get the security settings")
    @GetMapping("/security")
    public UserSecuritySettingsDto getSecuritySettings() {
        return userService.getSecuritySettings(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Start two-factor setup", description = "Returns the TOTP secret and QR code URI")
    @PostMapping("/security/2fa/enable")
    public TwoFactorSetupDto enableTwoFactor() {
        return userService.enableTwoFactor(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Verify a TOTP code and activate two-factor authentication")
    @PostMapping("/security/2fa/verify")
    public boolean verifyTwoFactor(@RequestBody Map<String, String> request) {
        return userService.verifyTwoFactor(SecurityUtils.getCurrentUserId(), request.get("code"));
    }

    @Operation(summary = "Disable two-factor authentication", description = "400 when the code is wrong")
    @PostMapping("/security/2fa/disable")
    public boolean disableTwoFactor(@RequestBody Map<String, String> request) {
        return userService.disableTwoFactor(SecurityUtils.getCurrentUserId(), request.get("code"));
    }

    @Operation(summary = "Export every piece of data of the account as JSON")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportUserData() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "user-data.json");
        return ResponseEntity.ok()
            .headers(headers)
            .body(userDataExportService.exportUserData(SecurityUtils.getCurrentUserId()));
    }
}
