package dev.byteworks.indezy.controller;

import dev.byteworks.indezy.dto.*;
import dev.byteworks.indezy.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@RestController
@RequestMapping("/users")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "User profile, preferences, and account management")
public class UserController {

    private final UserService userService;

    // For now, we'll use a hardcoded user ID until authentication is implemented
    private static final Long CURRENT_USER_ID = 1L;

    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Retrieve the current user's profile information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserDto> getUserProfile() {
        log.debug("GET /users/profile - Getting user profile");
        UserDto userProfile = userService.getUserProfile(CURRENT_USER_ID);
        return ResponseEntity.ok(userProfile);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update user profile", description = "Update the current user's profile information")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserDto> updateUserProfile(@Valid @RequestBody UserDto userDto) {
        log.debug("PUT /users/profile - Updating user profile");
        UserDto updatedProfile = userService.updateUserProfile(CURRENT_USER_ID, userDto);
        return ResponseEntity.ok(updatedProfile);
    }

    @PostMapping("/avatar")
    @Operation(summary = "Upload user avatar", description = "Upload a new avatar image for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avatar uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<String> uploadAvatar(
            @Parameter(description = "Avatar image file") @RequestParam("file") MultipartFile file) {
        log.debug("POST /users/avatar - Uploading avatar");
        try {
            String avatarUrl = userService.uploadAvatar(CURRENT_USER_ID, file);
            return ResponseEntity.ok(avatarUrl);
        } catch (IOException e) {
            log.error("Error uploading avatar", e);
            return ResponseEntity.badRequest().body("Error uploading file");
        }
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Change the current user's password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Password changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password data"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Boolean> changePassword(@Valid @RequestBody PasswordChangeRequestDto request) {
        log.debug("POST /users/change-password - Changing password");
        try {
            boolean success = userService.changePassword(CURRENT_USER_ID, request);
            return ResponseEntity.ok(success);
        } catch (IllegalArgumentException e) {
            log.error("Password change failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get user preferences", description = "Retrieve the current user's preferences")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserPreferencesDto> getUserPreferences() {
        log.debug("GET /users/preferences - Getting user preferences");
        UserPreferencesDto preferences = userService.getUserPreferences(CURRENT_USER_ID);
        return ResponseEntity.ok(preferences);
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update user preferences", description = "Update the current user's preferences")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid preference data"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserPreferencesDto> updateUserPreferences(@Valid @RequestBody UserPreferencesDto preferencesDto) {
        log.debug("PUT /users/preferences - Updating user preferences");
        UserPreferencesDto updatedPreferences = userService.updateUserPreferences(CURRENT_USER_ID, preferencesDto);
        return ResponseEntity.ok(updatedPreferences);
    }

    @GetMapping("/notifications")
    @Operation(summary = "Get notification settings", description = "Retrieve the current user's notification settings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification settings retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserNotificationSettingsDto> getNotificationSettings() {
        log.debug("GET /users/notifications - Getting notification settings");
        UserNotificationSettingsDto settings = userService.getNotificationSettings(CURRENT_USER_ID);
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/notifications")
    @Operation(summary = "Update notification settings", description = "Update the current user's notification settings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Notification settings updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid notification data"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserNotificationSettingsDto> updateNotificationSettings(@Valid @RequestBody UserNotificationSettingsDto settingsDto) {
        log.debug("PUT /users/notifications - Updating notification settings");
        UserNotificationSettingsDto updatedSettings = userService.updateNotificationSettings(CURRENT_USER_ID, settingsDto);
        return ResponseEntity.ok(updatedSettings);
    }

    @GetMapping("/security")
    @Operation(summary = "Get security settings", description = "Retrieve the current user's security settings")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Security settings retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserSecuritySettingsDto> getSecuritySettings() {
        log.debug("GET /users/security - Getting security settings");
        UserSecuritySettingsDto settings = userService.getSecuritySettings(CURRENT_USER_ID);
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/security/2fa/enable")
    @Operation(summary = "Enable two-factor authentication", description = "Enable 2FA for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "2FA enabled successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<String> enableTwoFactor(@RequestBody(required = false) Map<String, Object> body) {
        log.debug("POST /users/security/2fa/enable - Enabling 2FA");
        String qrCode = userService.enableTwoFactor(CURRENT_USER_ID);
        return ResponseEntity.ok(qrCode);
    }

    @PostMapping("/security/2fa/disable")
    @Operation(summary = "Disable two-factor authentication", description = "Disable 2FA for the current user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "2FA disabled successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid verification code"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Boolean> disableTwoFactor(@RequestBody Map<String, String> request) {
        log.debug("POST /users/security/2fa/disable - Disabling 2FA");
        try {
            String code = request.get("code");
            boolean success = userService.disableTwoFactor(CURRENT_USER_ID, code);
            return ResponseEntity.ok(success);
        } catch (IllegalArgumentException e) {
            log.error("2FA disable failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/security/sessions/{sessionId}")
    @Operation(summary = "Terminate session", description = "Terminate a specific user session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session terminated successfully"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    public ResponseEntity<Boolean> terminateSession(@PathVariable String sessionId) {
        log.debug("DELETE /users/security/sessions/{} - Terminating session", sessionId);
        boolean success = userService.terminateSession(CURRENT_USER_ID, sessionId);
        return ResponseEntity.ok(success);
    }

    @PostMapping("/account/delete")
    @Operation(summary = "Delete account", description = "Delete the current user's account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid password"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<Boolean> deleteAccount(@RequestBody Map<String, String> request) {
        log.debug("DELETE /users/account - Deleting account");
        try {
            String password = request.get("password");
            boolean success = userService.deleteAccount(CURRENT_USER_ID, password);
            return ResponseEntity.ok(success);
        } catch (IllegalArgumentException e) {
            log.error("Account deletion failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/export")
    @Operation(summary = "Export user data", description = "Export all user data as a downloadable file")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data exported successfully"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<byte[]> exportUserData() {
        log.debug("GET /users/export - Exporting user data");
        byte[] data = userService.exportUserData(CURRENT_USER_ID);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "user-data.json");
        
        return ResponseEntity.ok()
                .headers(headers)
                .body(data);
    }
}
