import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { UserManagementService, SecuritySettings } from './user-management.service';
import { environment } from '../../../environments/environment';
import {
  UserProfile,
  UserNotificationSettings,
  UserPreferences,
  PasswordChangeRequest
} from '../../models/user-management.models';

describe('UserManagementService', () => {
  let service: UserManagementService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/users`;

  // Mock data
  const mockUserProfile: UserProfile = {
    id: 1,
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@example.com',
    phone: '+1234567890',
    birthDate: '1990-01-01',
    address: '123 Main St',
    city: 'New York',
    avatar: 'avatar.jpg',
    bio: 'Software developer',
    company: 'Tech Corp',
    position: 'Senior Developer',
    website: 'https://johndoe.com',
    linkedin: 'https://linkedin.com/in/johndoe',
    github: 'https://github.com/johndoe',
    skills: ['JavaScript', 'TypeScript', 'Angular'],
    languages: ['English', 'French'],
    timezone: 'America/New_York',
    currency: 'USD'
  };

  const mockUserPreferences: UserPreferences = {
    theme: 'dark',
    language: 'en',
    dateFormat: 'MM/dd/yyyy',
    timeFormat: '12h',
    currency: 'USD',
    timezone: 'America/New_York',
    defaultView: 'dashboard',
    itemsPerPage: 10,
    autoSave: true
  };

  const mockNotificationSettings: UserNotificationSettings = {
    emailNotifications: true,
    pushNotifications: false,
    projectUpdates: true,
    clientMessages: true,
    systemAlerts: true,
    weeklyReports: false,
    marketingEmails: false
  };

  const mockSecuritySettings: SecuritySettings = {
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
        question: 'What is your mother\'s maiden name?',
        answer: 'Smith'
      }
    ]
  };

  const mockPasswordChangeRequest: PasswordChangeRequest = {
    currentPassword: 'oldPassword123',
    newPassword: 'newPassword456',
    confirmPassword: 'newPassword456'
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

    // Mock the initial loadUserProfile call in constructor
    const req = httpMock.expectOne(`${API_URL}/profile`);
    req.flush(mockUserProfile);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('Profile Management', () => {
    it('should get user profile', () => {
      service.getUserProfile().subscribe(profile => {
        expect(profile).toEqual(mockUserProfile);
      });

      const req = httpMock.expectOne(`${API_URL}/profile`);
      expect(req.request.method).toBe('GET');
      req.flush(mockUserProfile);
    });

    it('should update user profile', () => {
      const updateData = { firstName: 'Jane', lastName: 'Smith' };
      const updatedProfile = { ...mockUserProfile, ...updateData };

      service.updateUserProfile(updateData).subscribe(profile => {
        expect(profile).toEqual(updatedProfile);
      });

      const req = httpMock.expectOne(`${API_URL}/profile`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateData);
      req.flush(updatedProfile);
    });

    it('should upload avatar', () => {
      const mockFile = new File([''], 'avatar.jpg', { type: 'image/jpeg' });
      const avatarUrl = 'https://example.com/avatar.jpg';

      service.uploadAvatar(mockFile).subscribe(url => {
        expect(url).toBe(avatarUrl);
      });

      const req = httpMock.expectOne(`${API_URL}/avatar`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body instanceof FormData).toBeTruthy();
      req.flush(avatarUrl);
    });

    it('should handle profile update error', () => {
      const updateData = { firstName: 'Jane' };
      const errorMessage = 'Profile update failed';

      service.updateUserProfile(updateData).subscribe({
        next: () => fail('Should have failed'),
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne(`${API_URL}/profile`);
      req.flush({ message: errorMessage }, { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('Password Management', () => {
    it('should change password successfully', () => {
      service.changePassword(mockPasswordChangeRequest).subscribe(result => {
        expect(result).toBe(true);
      });

      const req = httpMock.expectOne(`${API_URL}/change-password`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(mockPasswordChangeRequest);
      req.flush(true);
    });

    it('should handle password change failure', () => {
      service.changePassword(mockPasswordChangeRequest).subscribe(result => {
        expect(result).toBe(false);
      });

      const req = httpMock.expectOne(`${API_URL}/change-password`);
      req.flush(false);
    });

    it('should handle password change error', () => {
      service.changePassword(mockPasswordChangeRequest).subscribe({
        next: () => fail('Should have failed'),
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne(`${API_URL}/change-password`);
      req.flush({ message: 'Invalid current password' }, { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('Preferences Management', () => {
    it('should get user preferences', () => {
      service.getUserPreferences().subscribe(preferences => {
        expect(preferences).toEqual(mockUserPreferences);
      });

      const req = httpMock.expectOne(`${API_URL}/preferences`);
      expect(req.request.method).toBe('GET');
      req.flush(mockUserPreferences);
    });

    it('should update user preferences', () => {
      const updateData = { theme: 'light' as const, itemsPerPage: 20 };
      const updatedPreferences = { ...mockUserPreferences, ...updateData };

      service.updateUserPreferences(updateData).subscribe(preferences => {
        expect(preferences).toEqual(updatedPreferences);
      });

      const req = httpMock.expectOne(`${API_URL}/preferences`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateData);
      req.flush(updatedPreferences);
    });
  });

  describe('Notification Settings', () => {
    it('should get notification settings', () => {
      service.getNotificationSettings().subscribe(settings => {
        expect(settings).toEqual(mockNotificationSettings);
      });

      const req = httpMock.expectOne(`${API_URL}/notifications`);
      expect(req.request.method).toBe('GET');
      req.flush(mockNotificationSettings);
    });

    it('should update notification settings', () => {
      const updateData = { emailNotifications: false, pushNotifications: true };
      const updatedSettings = { ...mockNotificationSettings, ...updateData };

      service.updateNotificationSettings(updateData).subscribe(settings => {
        expect(settings).toEqual(updatedSettings);
      });

      const req = httpMock.expectOne(`${API_URL}/notifications`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateData);
      req.flush(updatedSettings);
    });
  });

  describe('Security Management', () => {
    it('should get security settings', () => {
      service.getSecuritySettings().subscribe(settings => {
        expect(settings).toEqual(mockSecuritySettings);
      });

      const req = httpMock.expectOne(`${API_URL}/security`);
      expect(req.request.method).toBe('GET');
      req.flush(mockSecuritySettings);
    });

    it('should enable two-factor authentication', () => {
      const qrCode = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...';

      service.enableTwoFactor().subscribe(result => {
        expect(result).toBe(qrCode);
      });

      const req = httpMock.expectOne(`${API_URL}/security/2fa/enable`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({});
      req.flush(qrCode);
    });

    it('should disable two-factor authentication', () => {
      const code = '123456';

      service.disableTwoFactor(code).subscribe(result => {
        expect(result).toBe(true);
      });

      const req = httpMock.expectOne(`${API_URL}/security/2fa/disable`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ code });
      req.flush(true);
    });

    it('should terminate session', () => {
      const sessionId = 'session123';

      service.terminateSession(sessionId).subscribe(result => {
        expect(result).toBe(true);
      });

      const req = httpMock.expectOne(`${API_URL}/security/sessions/${sessionId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(true);
    });
  });

  describe('Account Management', () => {
    it('should delete account', () => {
      const password = 'password123';

      service.deleteAccount(password).subscribe(result => {
        expect(result).toBe(true);
      });

      const req = httpMock.expectOne(`${API_URL}/account`);
      expect(req.request.method).toBe('DELETE');
      expect(req.request.body).toEqual({ password });
      req.flush(true);
    });

    it('should export user data', () => {
      const mockBlob = new Blob(['user data'], { type: 'application/json' });

      service.exportUserData().subscribe(blob => {
        expect(blob).toEqual(mockBlob);
      });

      const req = httpMock.expectOne(`${API_URL}/export`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(mockBlob);
    });
  });

  describe('User Profile Observable', () => {
    it('should emit user profile updates', (done) => {
      // The service was already created in beforeEach and the constructor request was handled
      // So the observable should already have the profile data
      service.userProfile$.subscribe(profile => {
        // Since the constructor request was already handled in beforeEach,
        // the observable should emit the profile data
        expect(profile).toEqual(mockUserProfile);
        done();
      });
    });
  });
});
