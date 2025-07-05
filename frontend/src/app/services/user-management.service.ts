import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UserProfile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  avatar?: string;
  bio?: string;
  company?: string;
  position?: string;
  website?: string;
  linkedin?: string;
  github?: string;
  skills?: string[];
  languages?: string[];
  timezone?: string;
  currency?: string;
  notifications?: UserNotificationSettings;
  preferences?: UserPreferences;
}

export interface UserNotificationSettings {
  emailNotifications: boolean;
  pushNotifications: boolean;
  projectUpdates: boolean;
  clientMessages: boolean;
  systemAlerts: boolean;
  weeklyReports: boolean;
  marketingEmails: boolean;
}

export interface UserPreferences {
  theme: 'light' | 'dark' | 'auto';
  language: string;
  dateFormat: string;
  timeFormat: '12h' | '24h';
  currency: string;
  timezone: string;
  defaultView: 'dashboard' | 'projects' | 'clients';
  itemsPerPage: number;
  autoSave: boolean;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface SecuritySettings {
  twoFactorEnabled: boolean;
  lastPasswordChange: Date;
  loginSessions: LoginSession[];
  securityQuestions: SecurityQuestion[];
}

export interface LoginSession {
  id: string;
  device: string;
  browser: string;
  location: string;
  lastActive: Date;
  current: boolean;
}

export interface SecurityQuestion {
  id: number;
  question: string;
  answer?: string;
}

@Injectable({
  providedIn: 'root'
})
export class UserManagementService {
  private readonly API_URL = `${environment.apiUrl}/users`;
  
  private userProfileSubject = new BehaviorSubject<UserProfile | null>(null);
  public userProfile$ = this.userProfileSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadUserProfile();
  }

  // Profile Management
  getUserProfile(): Observable<UserProfile> {
    // Mock implementation - replace with real API call
    return of(this.getMockUserProfile());
  }

  updateUserProfile(profile: Partial<UserProfile>): Observable<UserProfile> {
    // Mock implementation - replace with real API call
    return new Observable(observer => {
      setTimeout(() => {
        const updatedProfile = { ...this.getMockUserProfile(), ...profile };
        this.userProfileSubject.next(updatedProfile);
        observer.next(updatedProfile);
        observer.complete();
      }, 1000);
    });
  }

  uploadAvatar(file: File): Observable<string> {
    // Mock implementation - replace with real API call
    return new Observable(observer => {
      setTimeout(() => {
        const mockAvatarUrl = 'assets/images/default-avatar.png';
        observer.next(mockAvatarUrl);
        observer.complete();
      }, 2000);
    });
  }

  // Password Management
  changePassword(passwordData: PasswordChangeRequest): Observable<boolean> {
    // Mock implementation - replace with real API call
    return new Observable(observer => {
      setTimeout(() => {
        if (passwordData.newPassword === passwordData.confirmPassword) {
          observer.next(true);
          observer.complete();
        } else {
          observer.error({ message: 'Passwords do not match' });
        }
      }, 1000);
    });
  }

  // Preferences Management
  getUserPreferences(): Observable<UserPreferences> {
    return of(this.getMockPreferences());
  }

  updateUserPreferences(preferences: Partial<UserPreferences>): Observable<UserPreferences> {
    return new Observable(observer => {
      setTimeout(() => {
        const updatedPreferences = { ...this.getMockPreferences(), ...preferences };
        observer.next(updatedPreferences);
        observer.complete();
      }, 500);
    });
  }

  // Notification Settings
  getNotificationSettings(): Observable<UserNotificationSettings> {
    return of(this.getMockNotificationSettings());
  }

  updateNotificationSettings(settings: Partial<UserNotificationSettings>): Observable<UserNotificationSettings> {
    return new Observable(observer => {
      setTimeout(() => {
        const updatedSettings = { ...this.getMockNotificationSettings(), ...settings };
        observer.next(updatedSettings);
        observer.complete();
      }, 500);
    });
  }

  // Security Management
  getSecuritySettings(): Observable<SecuritySettings> {
    return of(this.getMockSecuritySettings());
  }

  enableTwoFactor(): Observable<string> {
    // Returns QR code or setup key
    return of('mock-2fa-setup-key');
  }

  disableTwoFactor(code: string): Observable<boolean> {
    return of(true);
  }

  terminateSession(sessionId: string): Observable<boolean> {
    return of(true);
  }

  // Account Management
  deleteAccount(password: string): Observable<boolean> {
    return new Observable(observer => {
      setTimeout(() => {
        observer.next(true);
        observer.complete();
      }, 2000);
    });
  }

  exportUserData(): Observable<Blob> {
    // Mock implementation - returns user data as JSON
    return new Observable(observer => {
      setTimeout(() => {
        const userData = {
          profile: this.getMockUserProfile(),
          preferences: this.getMockPreferences(),
          notifications: this.getMockNotificationSettings()
        };
        const blob = new Blob([JSON.stringify(userData, null, 2)], { type: 'application/json' });
        observer.next(blob);
        observer.complete();
      }, 1000);
    });
  }

  private loadUserProfile(): void {
    this.getUserProfile().subscribe(profile => {
      this.userProfileSubject.next(profile);
    });
  }

  private getMockUserProfile(): UserProfile {
    return {
      id: 1,
      firstName: 'John',
      lastName: 'Doe',
      email: 'john.doe@example.com',
      phone: '+33 6 12 34 56 78',
      birthDate: '1990-05-15',
      address: '123 Rue de la Paix',
      city: 'Paris',
      avatar: 'assets/images/default-avatar.png',
      bio: 'Développeur Full Stack passionné avec 8 ans d\'expérience',
      company: 'Freelance',
      position: 'Développeur Senior',
      website: 'https://johndoe.dev',
      linkedin: 'https://linkedin.com/in/johndoe',
      github: 'https://github.com/johndoe',
      skills: ['Angular', 'React', 'Node.js', 'Python', 'PostgreSQL'],
      languages: ['Français', 'Anglais', 'Espagnol'],
      timezone: 'Europe/Paris',
      currency: 'EUR'
    };
  }

  private getMockPreferences(): UserPreferences {
    return {
      theme: 'light',
      language: 'fr',
      dateFormat: 'DD/MM/YYYY',
      timeFormat: '24h',
      currency: 'EUR',
      timezone: 'Europe/Paris',
      defaultView: 'dashboard',
      itemsPerPage: 25,
      autoSave: true
    };
  }

  private getMockNotificationSettings(): UserNotificationSettings {
    return {
      emailNotifications: true,
      pushNotifications: true,
      projectUpdates: true,
      clientMessages: true,
      systemAlerts: true,
      weeklyReports: false,
      marketingEmails: false
    };
  }

  private getMockSecuritySettings(): SecuritySettings {
    return {
      twoFactorEnabled: false,
      lastPasswordChange: new Date('2024-01-15'),
      loginSessions: [
        {
          id: '1',
          device: 'MacBook Pro',
          browser: 'Chrome 120',
          location: 'Paris, France',
          lastActive: new Date(),
          current: true
        },
        {
          id: '2',
          device: 'iPhone 15',
          browser: 'Safari Mobile',
          location: 'Paris, France',
          lastActive: new Date(Date.now() - 86400000),
          current: false
        }
      ],
      securityQuestions: [
        { id: 1, question: 'Quel est le nom de votre premier animal de compagnie ?' },
        { id: 2, question: 'Dans quelle ville êtes-vous né(e) ?' },
        { id: 3, question: 'Quel est le nom de jeune fille de votre mère ?' }
      ]
    };
  }
}
