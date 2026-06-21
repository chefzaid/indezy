package dev.swirlit.indezy.service;

import dev.swirlit.indezy.dto.PasswordChangeRequestDto;
import dev.swirlit.indezy.dto.UserDto;
import dev.swirlit.indezy.dto.UserNotificationSettingsDto;
import dev.swirlit.indezy.dto.UserPreferencesDto;
import dev.swirlit.indezy.dto.UserSecuritySettingsDto;
import dev.swirlit.indezy.exception.ResourceNotFoundException;
import dev.swirlit.indezy.mapper.UserMapper;
import dev.swirlit.indezy.model.User;
import dev.swirlit.indezy.model.UserSession;
import dev.swirlit.indezy.repository.UserRepository;
import dev.swirlit.indezy.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserDto testUserDto;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("john.doe@example.com");
        testUser.setFirstName("John");
        testUser.setLastName("Doe");
        testUser.setPasswordHash("hashed");

        testUserDto = new UserDto();
        testUserDto.setId(1L);
        testUserDto.setEmail("john.doe@example.com");
        testUserDto.setFirstName("John");
        testUserDto.setLastName("Doe");
    }

    @Test
    void getUserProfile_WithExistingId_ShouldReturnUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userMapper.toDto(testUser)).thenReturn(testUserDto);

        UserDto result = userService.getUserProfile(1L);

        assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
    }

    @Test
    void getUserProfile_WithNonExistingId_ShouldThrowResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateUserProfile_ShouldMapAndSave() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toDto(testUser)).thenReturn(testUserDto);

        UserDto result = userService.updateUserProfile(1L, testUserDto);

        assertThat(result).isNotNull();
        verify(userMapper).updateEntity(testUserDto, testUser);
        verify(userRepository).save(testUser);
    }

    @Test
    void changePassword_WithValidRequest_ShouldUpdateHash() {
        PasswordChangeRequestDto request = new PasswordChangeRequestDto();
        request.setCurrentPassword("old");
        request.setNewPassword("newPass123");
        request.setConfirmPassword("newPass123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("newHash");

        boolean result = userService.changePassword(1L, request);

        assertThat(result).isTrue();
        assertThat(testUser.getPasswordHash()).isEqualTo("newHash");
        assertThat(testUser.getLastPasswordChange()).isNotNull();
        verify(userRepository).save(testUser);
    }

    @Test
    void changePassword_WithMismatchedConfirmation_ShouldThrowIllegalArgumentException() {
        PasswordChangeRequestDto request = new PasswordChangeRequestDto();
        request.setCurrentPassword("old");
        request.setNewPassword("newPass123");
        request.setConfirmPassword("different");

        assertThatThrownBy(() -> userService.changePassword(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("do not match");
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_WithWrongCurrentPassword_ShouldThrowIllegalArgumentException() {
        PasswordChangeRequestDto request = new PasswordChangeRequestDto();
        request.setCurrentPassword("wrong");
        request.setNewPassword("newPass123");
        request.setConfirmPassword("newPass123");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("incorrect");
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUserPreferences_ShouldReturnPreferences() {
        UserPreferencesDto prefs = new UserPreferencesDto();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userMapper.toPreferencesDto(testUser)).thenReturn(prefs);

        assertThat(userService.getUserPreferences(1L)).isSameAs(prefs);
    }

    @Test
    void updateUserPreferences_ShouldMapAndSave() {
        UserPreferencesDto prefs = new UserPreferencesDto();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toPreferencesDto(testUser)).thenReturn(prefs);

        assertThat(userService.updateUserPreferences(1L, prefs)).isSameAs(prefs);
        verify(userMapper).updatePreferences(prefs, testUser);
    }

    @Test
    void getNotificationSettings_ShouldReturnSettings() {
        UserNotificationSettingsDto settings = new UserNotificationSettingsDto();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userMapper.toNotificationSettingsDto(testUser)).thenReturn(settings);

        assertThat(userService.getNotificationSettings(1L)).isSameAs(settings);
    }

    @Test
    void updateNotificationSettings_ShouldMapAndSave() {
        UserNotificationSettingsDto settings = new UserNotificationSettingsDto();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(testUser)).thenReturn(testUser);
        when(userMapper.toNotificationSettingsDto(testUser)).thenReturn(settings);

        assertThat(userService.updateNotificationSettings(1L, settings)).isSameAs(settings);
        verify(userMapper).updateNotificationSettings(settings, testUser);
    }

    @Test
    void getSecuritySettings_ShouldReturnSettings() {
        UserSecuritySettingsDto settings = new UserSecuritySettingsDto();
        when(userRepository.findByIdWithSecurityData(1L)).thenReturn(Optional.of(testUser));
        when(userMapper.toSecuritySettingsDto(testUser)).thenReturn(settings);

        assertThat(userService.getSecuritySettings(1L)).isSameAs(settings);
    }

    @Test
    void enableTwoFactor_ShouldStoreSecretAndReturnQrCode() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        String qrCode = userService.enableTwoFactor(1L);

        assertThat(qrCode).startsWith("data:image/png;base64,");
        assertThat(testUser.getTwoFactorEnabled()).isTrue();
        assertThat(testUser.getTwoFactorSecret()).isNotBlank();
        verify(userRepository).save(testUser);
    }

    @Test
    void disableTwoFactor_WithValidCode_ShouldClearSecret() {
        testUser.setTwoFactorEnabled(true);
        testUser.setTwoFactorSecret("secret");
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        boolean result = userService.disableTwoFactor(1L, "123456");

        assertThat(result).isTrue();
        assertThat(testUser.getTwoFactorEnabled()).isFalse();
        assertThat(testUser.getTwoFactorSecret()).isNull();
    }

    @Test
    void disableTwoFactor_WithInvalidCode_ShouldThrowIllegalArgumentException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.disableTwoFactor(1L, "123"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void terminateSession_WithOwnSession_ShouldDeleteIt() {
        UserSession session = new UserSession();
        User owner = new User();
        owner.setId(1L);
        session.setUser(owner);
        when(userSessionRepository.findBySessionId("abc")).thenReturn(Optional.of(session));

        boolean result = userService.terminateSession(1L, "abc");

        assertThat(result).isTrue();
        verify(userSessionRepository).delete(session);
    }

    @Test
    void terminateSession_WithForeignSession_ShouldThrowIllegalArgumentException() {
        UserSession session = new UserSession();
        User otherUser = new User();
        otherUser.setId(2L);
        session.setUser(otherUser);
        when(userSessionRepository.findBySessionId("abc")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> userService.terminateSession(1L, "abc"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(userSessionRepository, never()).delete(any());
    }

    @Test
    void terminateSession_WithUnknownSession_ShouldThrowResourceNotFoundException() {
        when(userSessionRepository.findBySessionId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.terminateSession(1L, "missing"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteAccount_WithCorrectPassword_ShouldSoftDeleteUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

        boolean result = userService.deleteAccount(1L, "pass");

        assertThat(result).isTrue();
        assertThat(testUser.getDeletedAt()).isNotNull();
        verify(userRepository).save(testUser);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void deleteAccount_WithWrongPassword_ShouldThrowIllegalArgumentException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.deleteAccount(1L, "wrong"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void findByEmail_WithExistingEmail_ShouldReturnUser() {
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(testUser));
        when(userMapper.toDto(testUser)).thenReturn(testUserDto);

        assertThat(userService.findByEmail("john.doe@example.com").getId()).isEqualTo(1L);
    }

    @Test
    void findByEmail_WithUnknownEmail_ShouldThrowResourceNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findByEmail("missing@example.com"))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void existsByEmail_ShouldDelegateToRepository() {
        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        assertThat(userService.existsByEmail("john.doe@example.com")).isTrue();
    }
}
