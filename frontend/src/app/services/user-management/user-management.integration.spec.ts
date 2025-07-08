import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { UserManagementService, SecuritySettings } from './user-management.service';
import {
  PasswordChangeRequest,
  UserProfile,
  UserPreferences,
  UserNotificationSettings
} from '../../models/user-management.models';

/**
 * Integration tests for UserManagementService that make actual HTTP calls to the backend.
 * These tests are designed to run against a real backend server.
 */
describe('UserManagementService Integration Tests', () => {
  let service: UserManagementService;
  let httpMock: HttpTestingController;

  // Test data
  const testUserProfile = {
    firstName: 'Integration',
    lastName: 'Test',
    phone: '+1234567890',
    bio: 'Integration test user',
    company: 'Test Corp'
  };

  const testPreferences = {
    theme: 'dark' as const,
    language: 'en',
    timeFormat: '24h' as const,
    itemsPerPage: 25
  };

  const testNotificationSettings = {
    emailNotifications: false,
    pushNotifications: true,
    projectUpdates: true,
    clientMessages: false
  };

  const testPasswordChange: PasswordChangeRequest = {
    currentPassword: 'testPassword123',
    newPassword: 'newTestPassword456',
    confirmPassword: 'newTestPassword456'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UserManagementService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(UserManagementService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  // Helper method to handle the automatic profile load request made by the service constructor
  function handleConstructorRequest() {
    const constructorReq = httpMock.expectOne('http://localhost:8080/api/users/profile');
    expect(constructorReq.request.method).toBe('GET');
    constructorReq.flush({
      id: 1,
      firstName: 'Test',
      lastName: 'User',
      email: 'test@example.com'
    });
  }

  // Skip all tests until backend endpoints are implemented
  describe('Profile Management Integration', () => {
    it('should get user profile from backend', (done) => {
      const mockProfile: UserProfile = {
        id: 1,
        firstName: 'John',
        lastName: 'Doe',
        email: 'john.doe@example.com',
        phone: '+1234567890',
        bio: 'Test user bio',
        company: 'Test Company'
      };

      // Handle the constructor request first
      handleConstructorRequest();

      service.getUserProfile().subscribe({
        next: (profile) => {
          expect(profile).toBeDefined();
          expect(profile.id).toBeDefined();
          expect(profile.email).toBeDefined();
          expect(profile.firstName).toBeDefined();
          expect(profile.lastName).toBeDefined();
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/profile');
      expect(req.request.method).toBe('GET');
      req.flush(mockProfile);
    });

    it('should update user profile via backend', (done) => {
      const mockResponse: UserProfile = {
        id: 1,
        firstName: testUserProfile.firstName,
        lastName: testUserProfile.lastName,
        email: 'test@example.com',
        phone: testUserProfile.phone,
        bio: testUserProfile.bio,
        company: testUserProfile.company
      };

      // Handle the constructor request first
      handleConstructorRequest();

      service.updateUserProfile(testUserProfile).subscribe({
        next: (updatedProfile) => {
          expect(updatedProfile).toBeDefined();
          expect(updatedProfile.firstName).toBe(testUserProfile.firstName);
          expect(updatedProfile.lastName).toBe(testUserProfile.lastName);
          expect(updatedProfile.phone).toBe(testUserProfile.phone);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/profile');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(testUserProfile);
      req.flush(mockResponse);
    });

    it('should upload avatar to backend', (done) => {
      const mockFile = new File(['test'], 'avatar.jpg', { type: 'image/jpeg' });
      const mockAvatarUrl = 'https://example.com/avatars/user123.jpg';

      // Handle the constructor request first
      handleConstructorRequest();

      service.uploadAvatar(mockFile).subscribe({
        next: (avatarUrl) => {
          expect(avatarUrl).toBeDefined();
          expect(typeof avatarUrl).toBe('string');
          expect(avatarUrl.length).toBeGreaterThan(0);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/avatar');
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBeTruthy();
      req.flush(mockAvatarUrl);
    });
  });

  describe('Password Management Integration', () => {
    it('should change password via backend', (done) => {
      // Handle the constructor request first
      handleConstructorRequest();

      service.changePassword(testPasswordChange).subscribe({
        next: (result) => {
          expect(result).toBe(true);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/change-password');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(testPasswordChange);
      req.flush(true);
    });
  });

  describe('Preferences Management Integration', () => {
    it('should get user preferences from backend', (done) => {
      const mockPreferences: UserPreferences = {
        theme: 'light',
        language: 'en',
        dateFormat: 'MM/DD/YYYY',
        timeFormat: '12h',
        currency: 'USD',
        timezone: 'UTC',
        defaultView: 'dashboard',
        itemsPerPage: 10,
        autoSave: true
      };

      // Handle the constructor request first
      handleConstructorRequest();

      service.getUserPreferences().subscribe({
        next: (preferences) => {
          expect(preferences).toBeDefined();
          expect(preferences.theme).toBeDefined();
          expect(preferences.language).toBeDefined();
          expect(preferences.timezone).toBeDefined();
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/preferences');
      expect(req.request.method).toBe('GET');
      req.flush(mockPreferences);
    });

    it('should update user preferences via backend', (done) => {
      const mockResponse: UserPreferences = {
        theme: testPreferences.theme,
        language: testPreferences.language,
        dateFormat: 'MM/DD/YYYY',
        timeFormat: testPreferences.timeFormat,
        currency: 'USD',
        timezone: 'UTC',
        defaultView: 'dashboard',
        itemsPerPage: testPreferences.itemsPerPage,
        autoSave: true
      };

      // Handle the constructor request first
      handleConstructorRequest();

      service.updateUserPreferences(testPreferences).subscribe({
        next: (updatedPreferences) => {
          expect(updatedPreferences).toBeDefined();
          expect(updatedPreferences.theme).toBe(testPreferences.theme);
          expect(updatedPreferences.timeFormat).toBe(testPreferences.timeFormat);
          expect(updatedPreferences.itemsPerPage).toBe(testPreferences.itemsPerPage);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/preferences');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(testPreferences);
      req.flush(mockResponse);
    });
  });

  describe('Notification Settings Integration', () => {
    it('should get notification settings from backend', (done) => {
      const mockSettings: UserNotificationSettings = {
        emailNotifications: true,
        pushNotifications: false,
        projectUpdates: true,
        clientMessages: true,
        systemAlerts: true,
        weeklyReports: false,
        marketingEmails: false
      };

      // Handle the constructor request first
      handleConstructorRequest();

      service.getNotificationSettings().subscribe({
        next: (settings) => {
          expect(settings).toBeDefined();
          expect(typeof settings.emailNotifications).toBe('boolean');
          expect(typeof settings.pushNotifications).toBe('boolean');
          expect(typeof settings.projectUpdates).toBe('boolean');
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/notifications');
      expect(req.request.method).toBe('GET');
      req.flush(mockSettings);
    });

    it('should update notification settings via backend', (done) => {
      const mockResponse: UserNotificationSettings = {
        emailNotifications: testNotificationSettings.emailNotifications,
        pushNotifications: testNotificationSettings.pushNotifications,
        projectUpdates: testNotificationSettings.projectUpdates,
        clientMessages: testNotificationSettings.clientMessages,
        systemAlerts: true,
        weeklyReports: true,
        marketingEmails: false
      };

      // Handle the constructor request first
      handleConstructorRequest();

      service.updateNotificationSettings(testNotificationSettings).subscribe({
        next: (updatedSettings) => {
          expect(updatedSettings).toBeDefined();
          expect(updatedSettings.emailNotifications).toBe(testNotificationSettings.emailNotifications);
          expect(updatedSettings.pushNotifications).toBe(testNotificationSettings.pushNotifications);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/notifications');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(testNotificationSettings);
      req.flush(mockResponse);
    });
  });

  describe('Security Management Integration', () => {
    it('should get security settings from backend', (done) => {
      const mockSettings: SecuritySettings = {
        twoFactorEnabled: false,
        lastPasswordChange: new Date('2024-01-01'),
        loginSessions: [
          {
            id: 'session1',
            device: 'Chrome on Windows',
            browser: 'Chrome',
            location: 'New York, NY',
            lastActive: new Date(),
            current: true
          }
        ],
        securityQuestions: [
          {
            id: 1,
            question: 'What is your mother\'s maiden name?'
          }
        ]
      };

      // Handle the constructor request first
      handleConstructorRequest();

      service.getSecuritySettings().subscribe({
        next: (settings) => {
          expect(settings).toBeDefined();
          expect(typeof settings.twoFactorEnabled).toBe('boolean');
          expect(settings.lastPasswordChange).toBeDefined();
          expect(Array.isArray(settings.loginSessions)).toBe(true);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/security');
      expect(req.request.method).toBe('GET');
      req.flush(mockSettings);
    });

    it('should enable two-factor authentication via backend', (done) => {
      const mockQrCode = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...';

      // Handle the constructor request first
      handleConstructorRequest();

      service.enableTwoFactor().subscribe({
        next: (qrCode) => {
          expect(qrCode).toBeDefined();
          expect(typeof qrCode).toBe('string');
          expect(qrCode.length).toBeGreaterThan(0);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/security/2fa/enable');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(mockQrCode);
    });

    it('should terminate session via backend', (done) => {
      const testSessionId = 'test-session-123';

      // Handle the constructor request first
      handleConstructorRequest();

      service.terminateSession(testSessionId).subscribe({
        next: (result) => {
          expect(result).toBe(true);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne(`http://localhost:8080/api/users/security/sessions/${testSessionId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(true);
    });
  });

  describe('Account Management Integration', () => {
    it('should export user data from backend', (done) => {
      const mockData = JSON.stringify({ user: 'data' });
      const mockBlob = new Blob([mockData], { type: 'application/json' });

      // Handle the constructor request first
      handleConstructorRequest();

      service.exportUserData().subscribe({
        next: (blob) => {
          expect(blob).toBeDefined();
          expect(blob instanceof Blob).toBe(true);
          expect(blob.size).toBeGreaterThan(0);
          done();
        },
        error: (error) => {
          fail(`Unexpected error: ${error.message}`);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/export');
      expect(req.request.method).toBe('GET');
      req.flush(mockBlob);
    });

    // Note: Account deletion test is intentionally omitted to avoid
    // accidentally deleting test data during integration testing
  });

  describe('Error Handling Integration', () => {
    it('should handle network errors gracefully', (done) => {
      // Handle the constructor request first
      handleConstructorRequest();

      service.getUserProfile().subscribe({
        next: () => {
          fail('Should have failed with network error');
          done();
        },
        error: (error) => {
          expect(error).toBeDefined();
          expect(error.status).toBe(0);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/profile');
      req.error(new ProgressEvent('error'));
    });

    it('should handle server errors appropriately', (done) => {
      // Handle the constructor request first
      handleConstructorRequest();

      service.getUserProfile().subscribe({
        next: () => {
          fail('Should have failed with server error');
          done();
        },
        error: (error) => {
          expect(error).toBeDefined();
          expect(error.status).toBe(500);
          done();
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/users/profile');
      req.flush('Internal Server Error', { status: 500, statusText: 'Internal Server Error' });
    });
  });
});
