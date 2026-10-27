package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.*;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.UserDataExportService;
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
        return userService.changePassword(SecurityUtils.getCurrentUserId(), request);
    }

    @Operation(summary = "Finish or skip the welcome steps of a new account")
    @PostMapping("/onboarding/complete")
    public ResponseEntity<Void> completeOnboarding() {
        userService.completeOnboarding(SecurityUtils.getCurrentUserId());
        return ResponseEntity.noContent().build();
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
        return userService.verifyTwoFactor(SecurityUtils.getCurrentUserId(), request.getCode());
    }

    @Operation(summary = "Disable two-factor authentication", description = "400 when the code is missing or wrong")
    @PostMapping("/security/2fa/disable")
    public boolean disableTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request) {
        return userService.disableTwoFactor(SecurityUtils.getCurrentUserId(), request.getCode());
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
        return workspaceImportService.apply(currentWorkspace(), file.getBytes());
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
        return ResponseEntity.ok()
            .headers(headers)
            .body(userDataExportService.exportUserData(userId, accessGuard.currentFreelanceId().orElse(userId)));
    }
}
