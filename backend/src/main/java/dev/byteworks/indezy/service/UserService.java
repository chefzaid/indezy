package dev.byteworks.indezy.service;

import dev.byteworks.indezy.dto.*;
import dev.byteworks.indezy.exception.ResourceNotFoundException;
import dev.byteworks.indezy.mapper.UserMapper;
import dev.byteworks.indezy.model.User;
import dev.byteworks.indezy.model.UserSession;
import dev.byteworks.indezy.repository.UserRepository;
import dev.byteworks.indezy.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    private static final String UPLOAD_DIR = "uploads/avatars/";

    /**
     * Get user profile by ID
     */
    @Transactional(readOnly = true)
    public UserDto getUserProfile(Long userId) {
        log.debug("Getting user profile for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toDto(user);
    }

    /**
     * Update user profile
     */
    public UserDto updateUserProfile(Long userId, UserDto userDto) {
        log.debug("Updating user profile for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        userMapper.updateEntity(userDto, user);
        User savedUser = userRepository.save(user);
        return userMapper.toDto(savedUser);
    }

    /**
     * Upload user avatar
     */
    public String uploadAvatar(Long userId, MultipartFile file) throws IOException {
        log.debug("Uploading avatar for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null ? 
            originalFilename.substring(originalFilename.lastIndexOf('.')) : ".jpg";
        String filename = userId + "_" + UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(filename);

        // Save file
        Files.copy(file.getInputStream(), filePath);

        // Update user avatar path
        String avatarUrl = "/api/files/avatars/" + filename;
        user.setAvatar(avatarUrl);
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setLastPasswordChange(LocalDateTime.now());
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toPreferencesDto(user);
    }

    /**
     * Update user preferences
     */
    public UserPreferencesDto updateUserPreferences(Long userId, UserPreferencesDto preferencesDto) {
        log.debug("Updating user preferences for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toNotificationSettingsDto(user);
    }

    /**
     * Update notification settings
     */
    public UserNotificationSettingsDto updateNotificationSettings(Long userId, UserNotificationSettingsDto settingsDto) {
        log.debug("Updating notification settings for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return userMapper.toSecuritySettingsDto(user);
    }

    /**
     * Enable two-factor authentication
     */
    public String enableTwoFactor(Long userId) {
        log.debug("Enabling 2FA for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Generate secret and QR code (simplified implementation)
        String secret = UUID.randomUUID().toString();
        user.setTwoFactorSecret(secret);
        user.setTwoFactorEnabled(true);
        userRepository.save(user);

        // In a real implementation, this would generate a proper QR code
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
    }

    /**
     * Disable two-factor authentication
     */
    public boolean disableTwoFactor(Long userId, String code) {
        log.debug("Disabling 2FA for user ID: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // In a real implementation, this would verify the TOTP code
        // For now, we'll accept any 6-digit code
        if (code == null || code.length() != 6) {
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Password is incorrect");
        }

        userRepository.delete(user);
        return true;
    }

    /**
     * Export user data
     */
    @Transactional(readOnly = true)
    public byte[] exportUserData(Long userId) {
        log.debug("Exporting user data for user ID: {}", userId);
        User user = userRepository.findByIdWithSecurityData(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UserDto userDto = userMapper.toDto(user);
        
        // Convert to JSON (simplified implementation)
        String jsonData = "{\n" +
                "  \"profile\": {\n" +
                "    \"firstName\": \"" + userDto.getFirstName() + "\",\n" +
                "    \"lastName\": \"" + userDto.getLastName() + "\",\n" +
                "    \"email\": \"" + userDto.getEmail() + "\"\n" +
                "  }\n" +
                "}";

        return jsonData.getBytes();
    }

    /**
     * Find user by email
     */
    @Transactional(readOnly = true)
    public UserDto findByEmail(String email) {
        log.debug("Finding user by email: {}", email);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
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
