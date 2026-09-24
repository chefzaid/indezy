package dev.swirlit.indezy.service;

import dev.swirlit.indezy.constants.ErrorMessages;
import dev.swirlit.indezy.dto.*;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.UserMapper;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.UserSession;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = IOException.class)
public class UserService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;

    private static final String TWO_FACTOR_ISSUER = "Indezy";


    /**
     * Get user profile by ID
     */
    @Transactional(readOnly = true)
    public UserDto getUserProfile(Long userId) {
        log.debug("Getting user profile for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
        return userMapper.toDto(user);
    }

    /**
     * Update user profile
     */
    public UserDto updateUserProfile(Long userId, UserDto userDto) {
        log.debug("Updating user profile for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
        
        userMapper.updateEntity(userDto, user);
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    /** Largest accepted avatar image; the web app downscales photos to 256px before uploading. */
    static final long MAX_AVATAR_BYTES = 512L * 1024;
    private static final Set<String> AVATAR_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    /**
     * Stores the user's avatar as a data URL in the database and returns it. Keeping the image
     * in the database works on read-only, ephemeral container filesystems and needs no file route.
     */
    public String uploadAvatar(Long userId, MultipartFile file) throws IOException {
        log.debug("Uploading avatar for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Avatar file is empty");
        }
        String contentType = file.getContentType() != null ? file.getContentType().toLowerCase(Locale.ROOT) : "";
        if (!AVATAR_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Avatar must be a PNG, JPEG, WebP or GIF image");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Avatar image is larger than 512 KB");
        }

        String avatarUrl = "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(file.getBytes());
        user.setAvatarImage(avatarUrl);
        userRepository.save(user);
        return avatarUrl;
    }

    /**
     * Change user password
     */
    public boolean changePassword(Long userId, PasswordChangeRequestDto request) {
        log.debug("Changing password for user ID: {}", userId);
        
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation do not match");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setLastPasswordChange(LocalDateTime.now(ZoneId.systemDefault()));
        userRepository.save(user);

        return true;
    }

    /**
     * Get user preferences
     */
    @Transactional(readOnly = true)
    public UserPreferencesDto getUserPreferences(Long userId) {
        log.debug("Getting user preferences for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
        return userMapper.toPreferencesDto(user);
    }

    /**
     * Update user preferences
     */
    public UserPreferencesDto updateUserPreferences(Long userId, UserPreferencesDto preferencesDto) {
        log.debug("Updating user preferences for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
        
        userMapper.updatePreferences(preferencesDto, user);
        User savedUser = userRepository.save(user);
        return userMapper.toPreferencesDto(savedUser);
    }

    /**
     * Get notification settings
     */
    @Transactional(readOnly = true)
    public UserNotificationSettingsDto getNotificationSettings(Long userId) {
        log.debug("Getting notification settings for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
        return userMapper.toNotificationSettingsDto(user);
    }

    /**
     * Update notification settings
     */
    public UserNotificationSettingsDto updateNotificationSettings(Long userId, UserNotificationSettingsDto settingsDto) {
        log.debug("Updating notification settings for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
        
        userMapper.updateNotificationSettings(settingsDto, user);
        User savedUser = userRepository.save(user);
        return userMapper.toNotificationSettingsDto(savedUser);
    }

    /**
     * Get security settings
     */
    @Transactional(readOnly = true)
    public UserSecuritySettingsDto getSecuritySettings(Long userId) {
        log.debug("Getting security settings for user ID: {}", userId);
        User user = userRepository.findByIdWithSecurityData(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));
        return userMapper.toSecuritySettingsDto(user);
    }

    /**
     * Starts two-factor setup: generates and stores a fresh secret (leaving 2FA disabled until a
     * code is verified) and returns it with the provisioning URI for an authenticator app.
     */
    public TwoFactorSetupDto enableTwoFactor(Long userId) {
        log.debug("Starting 2FA setup for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));

        String secret = totpService.generateSecret();
        user.setTwoFactorSecret(secret);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);

        String otpauthUri = totpService.buildProvisioningUri(secret, user.getEmail(), TWO_FACTOR_ISSUER);
        return new TwoFactorSetupDto(secret, otpauthUri);
    }

    /** Confirms setup by validating a code against the stored secret and activating 2FA on success. */
    public boolean verifyTwoFactor(Long userId, String code) {
        log.debug("Verifying 2FA code for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));

        if (user.getTwoFactorSecret() == null || !totpService.validateCode(user.getTwoFactorSecret(), code)) {
            return false;
        }
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        return true;
    }

    /**
     * Disable two-factor authentication after validating a current code.
     */
    public boolean disableTwoFactor(Long userId, String code) {
        log.debug("Disabling 2FA for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));

        if (!totpService.validateCode(user.getTwoFactorSecret(), code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);

        return true;
    }

    /**
     * Terminate user session
     */
    public boolean terminateSession(Long userId, String sessionId) {
        log.debug("Terminating session {} for user ID: {}", sessionId, userId);
        
        UserSession session = userSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found: " + sessionId));

        if (!session.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Session does not belong to user");
        }

        userSessionRepository.delete(session);
        return true;
    }

    /**
     * Delete user account
     */
    public boolean deleteAccount(Long userId, String password) {
        log.debug("Deleting account for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_NOT_FOUND, userId)));

        // Verify password (confirmation that the account owner requested the deletion)
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Password is incorrect");
        }

        // Soft delete: keep the record but mark it deleted so the account can no longer log in.
        user.setDeletedAt(LocalDateTime.now(ZoneId.systemDefault()));
        userRepository.save(user);
        return true;
    }

    /**
     * Find user by email
     */
    @Transactional(readOnly = true)
    public UserDto findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(ErrorMessages.USER_EMAIL_NOT_FOUND, email)));
        return userMapper.toDto(user);
    }

    /**
     * Check if user exists by email
     */
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
