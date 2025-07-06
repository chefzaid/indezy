import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  UserProfile,
  UserNotificationSettings,
  UserPreferences,
  PasswordChangeRequest
} from '../../models/user-management.models';

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

  private readonly userProfileSubject = new BehaviorSubject<UserProfile | null>(null);
  public userProfile$ = this.userProfileSubject.asObservable();

  constructor(private readonly http: HttpClient) {
    this.loadUserProfile();
  }

  // Profile Management
  getUserProfile(): Observable<UserProfile> {
    return this.http.get<UserProfile>(`${this.API_URL}/profile`);
  }

  updateUserProfile(profile: Partial<UserProfile>): Observable<UserProfile> {
    return this.http.put<UserProfile>(`${this.API_URL}/profile`, profile);
  }

  uploadAvatar(file: File): Observable<string> {
    const formData = new FormData();
    formData.append('avatar', file);
    return this.http.post<string>(`${this.API_URL}/avatar`, formData);
  }

  // Password Management
  changePassword(passwordData: PasswordChangeRequest): Observable<boolean> {
    return this.http.post<boolean>(`${this.API_URL}/change-password`, passwordData);
  }

  // Preferences Management
  getUserPreferences(): Observable<UserPreferences> {
    return this.http.get<UserPreferences>(`${this.API_URL}/preferences`);
  }

  updateUserPreferences(preferences: Partial<UserPreferences>): Observable<UserPreferences> {
    return this.http.put<UserPreferences>(`${this.API_URL}/preferences`, preferences);
  }

  // Notification Settings
  getNotificationSettings(): Observable<UserNotificationSettings> {
    return this.http.get<UserNotificationSettings>(`${this.API_URL}/notifications`);
  }

  updateNotificationSettings(settings: Partial<UserNotificationSettings>): Observable<UserNotificationSettings> {
    return this.http.put<UserNotificationSettings>(`${this.API_URL}/notifications`, settings);
  }

  // Security Management
  getSecuritySettings(): Observable<SecuritySettings> {
    return this.http.get<SecuritySettings>(`${this.API_URL}/security`);
  }

  enableTwoFactor(): Observable<string> {
    return this.http.post<string>(`${this.API_URL}/security/2fa/enable`, {});
  }

  disableTwoFactor(code: string): Observable<boolean> {
    return this.http.post<boolean>(`${this.API_URL}/security/2fa/disable`, { code });
  }

  terminateSession(sessionId: string): Observable<boolean> {
    return this.http.delete<boolean>(`${this.API_URL}/security/sessions/${sessionId}`);
  }

  // Account Management
  deleteAccount(password: string): Observable<boolean> {
    return this.http.delete<boolean>(`${this.API_URL}/account`, { body: { password } });
  }

  exportUserData(): Observable<Blob> {
    return this.http.get(`${this.API_URL}/export`, { responseType: 'blob' });
  }

  private loadUserProfile(): void {
    this.getUserProfile().subscribe(profile => {
      this.userProfileSubject.next(profile);
    });
  }
}
