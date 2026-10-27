package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.admin.AdminService;
import dev.swirlit.indezy.dto.*;
import dev.swirlit.indezy.model.enums.AccountEventType;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.AccountEventService;
import dev.swirlit.indezy.service.AccountSessionService;
import dev.swirlit.indezy.service.UserDataExportService;
import dev.swirlit.indezy.service.FeatureFlagService;
import dev.swirlit.indezy.service.PublicProfileService;
import dev.swirlit.indezy.service.UserService;
import dev.swirlit.indezy.service.WorkspaceImportService;
import dev.swirlit.indezy.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/** The signed-in account: profile, preferences, notifications, security and data export. */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "Profile, preferences and security of the signed-in account")
public class UserController {

    private final UserService userService;
    private final UserDataExportService userDataExportService;
    private final WorkspaceImportService workspaceImportService;
    private final PublicProfileService publicProfileService;
    private final AdminService adminService;
    private final AccountEventService accountEvents;
    private final AccountSessionService accountSessions;
    private final FeatureFlagService featureFlagService;
    private final AccessGuard accessGuard;

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

    @Operation(summary = "Change the password",
        description = "400 when the new password is under 8 characters or over 72 bytes, the confirmation differs, or the current password is wrong")
    @PostMapping("/change-password")
    public boolean changePassword(@Valid @RequestBody PasswordChangeRequestDto request) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean changed = userService.changePassword(userId, request);
        if (changed) {
            // Whoever knew the old password is signed out everywhere else.
            accountEvents.record(userId, AccountEventType.PASSWORD_CHANGED);
            accountSessions.endOthers(userId, SecurityUtils.getCurrentSessionId());
        }
        return changed;
    }

    @Operation(summary = "Finish or skip the welcome steps of a new account")
    @PostMapping("/onboarding/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> completeOnboarding() {
        userService.completeOnboarding(SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "What the account may open: the admin console, and which features are on")
    @GetMapping("/capabilities")
    public AdminDtos.Capabilities capabilities() {
        return new AdminDtos.Capabilities(adminService.isAdmin(SecurityUtils.getCurrentUserId()), featureFlagService.all());
    }

    @Operation(summary = "Whether the public profile is shown, and its address name")
    @GetMapping("/public-profile")
    public PublicProfileSettingsDto getPublicProfile() {
        return publicProfileService.settings(SecurityUtils.getCurrentUserId());
    }

    @Operation(summary = "Show or hide the public profile and choose its address name",
        description = "400 without an address when showing it, 409 for an address another account took")
    @PutMapping("/public-profile")
    public PublicProfileSettingsDto updatePublicProfile(@Valid @RequestBody PublicProfileSettingsDto settings) {
        return publicProfileService.update(SecurityUtils.getCurrentUserId(), settings);
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

    @Operation(summary = "Verify a TOTP code and activate two-factor authentication",
        description = "false when the code is wrong; 400 when it is missing")
    @PostMapping("/security/2fa/verify")
    public boolean verifyTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean enabled = userService.verifyTwoFactor(userId, request.getCode());
        if (enabled) {
            accountEvents.record(userId, AccountEventType.TWO_FACTOR_ENABLED);
        }
        return enabled;
    }

    @Operation(summary = "Disable two-factor authentication", description = "400 when the code is missing or wrong")
    @PostMapping("/security/2fa/disable")
    public boolean disableTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        boolean disabled = userService.disableTwoFactor(userId, request.getCode());
        if (disabled) {
            accountEvents.record(userId, AccountEventType.TWO_FACTOR_DISABLED);
        }
        return disabled;
    }

    @Operation(summary = "Preview the import of an account export into the workspace",
        description = "Lists each record as new, already there or invalid, writing nothing; 400 for a file that is not an export")
    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceImportReport previewImport(@RequestParam("file") MultipartFile file) throws IOException {
        return workspaceImportService.preview(currentWorkspace(), file.getBytes());
    }

    @Operation(summary = "Import an account export into the workspace",
        description = "Adds the new records and leaves those already there; the report says what was done")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkspaceImportReport importData(@RequestParam("file") MultipartFile file) throws IOException {
        WorkspaceImportReport report = workspaceImportService.apply(currentWorkspace(), file.getBytes());
        accountEvents.record(SecurityUtils.getCurrentUserId(), AccountEventType.DATA_IMPORTED);
        return report;
    }

    private Long currentWorkspace() {
        return accessGuard.currentFreelanceId().orElseThrow(() -> new AccessDeniedException("No workspace for this account"));
    }

    @Operation(summary = "Export every piece of data of the account as JSON")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportUserData() {
        // The account's workspace is found by its email, like every other resource: user and workspace ids differ.
        Long userId = SecurityUtils.getCurrentUserId();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "user-data.json");
        accountEvents.record(userId, AccountEventType.DATA_EXPORTED);
        return ResponseEntity.ok()
            .headers(headers)
            .body(userDataExportService.exportUserData(userId, accessGuard.currentFreelanceId().orElse(userId)));
    }
}
