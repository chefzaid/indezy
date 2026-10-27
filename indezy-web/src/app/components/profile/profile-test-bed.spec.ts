import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';

import { SkillNamesService } from '../../services/skill/skill-names.service';
import { SkillNames } from '../../shared/utils/skill-names';
import { UserNotificationSettings, UserPreferences, UserProfile } from '../../models/user-management.models';
import { NotificationService } from '../../services/notification/notification.service';
import { PublicProfileService } from '../../services/public-profile/public-profile.service';
import { AccountSecurityService } from '../../services/account-security/account-security.service';
import { WebhookService } from '../../services/webhook/webhook.service';
import { UserManagementService } from '../../services/user-management/user-management.service';
import { LANGUAGE_STORAGE_KEY } from '../../shared/locale/app-locale';
import { ThemeService } from '../../shared/theme/theme.service';
import { ProfileComponent } from './profile.component';

/** The profile page's test bed, shared by its spec files: services faked, the saved language put back after each test. */
export const profile: UserProfile = {
  id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: '0600000000', birthDate: '1990-05-12',
  city: 'Paris', position: 'Tech lead', skills: ['Java', 'Angular'], languages: ['Anglais'], targetDailyRate: 650,
  availabilityDate: '2026-11-02', timezone: 'Europe/Paris', currency: 'EUR'
};

export const preferences = {
  theme: 'dark', language: 'fr', dateFormat: 'dd/MM/yyyy', timeFormat: '24h', currency: 'EUR', timezone: 'Europe/Paris',
  defaultView: 'projects', itemsPerPage: '50', autoSave: false, inactivityDays: 30
} as unknown as UserPreferences;

export const notificationSettings: UserNotificationSettings = { weeklyReports: true, weeklyDigestSections: ['PIPELINE'],
  bookingEmails: true, newOffersBadge: true, emailsAvailable: true };

export interface ProfileTestBed {
  users: jasmine.SpyObj<UserManagementService>;
  notifications: jasmine.SpyObj<NotificationService>;
  theme: jasmine.SpyObj<ThemeService>;
  navigate: jasmine.Spy;
  consoleError: jasmine.Spy;
  create(loaded?: Observable<UserProfile>, prefs?: UserPreferences): ComponentFixture<ProfileComponent>;
  text(fixture: ComponentFixture<ProfileComponent>): string;
}

/** Registers the hooks of a profile spec and answers its test bed. */
export function profileTestBed(): ProfileTestBed {
  let savedLanguage: string | null;
  const bed = {
    create(loaded: Observable<UserProfile> = of(profile), prefs: UserPreferences = preferences): ComponentFixture<ProfileComponent> {
      bed.users = jasmine.createSpyObj('UserManagementService', ['getUserProfile', 'updateUserProfile', 'uploadAvatar',
        'changePassword', 'getUserPreferences', 'updateUserPreferences', 'getNotificationSettings', 'updateNotificationSettings',
        'getSecuritySettings', 'enableTwoFactor', 'verifyTwoFactor', 'disableTwoFactor', 'exportUserData']);
      bed.users.getUserProfile.and.returnValue(loaded);
      bed.users.getUserPreferences.and.returnValue(of(prefs));
      bed.users.getNotificationSettings.and.returnValue(of(notificationSettings));
      bed.users.getSecuritySettings.and.returnValue(of({ twoFactorEnabled: false, lastPasswordChange: new Date() }));
      bed.notifications = jasmine.createSpyObj('NotificationService', ['success', 'error', 'successWithAction']);
      bed.theme = jasmine.createSpyObj('ThemeService', ['apply']);
      TestBed.configureTestingModule({
        imports: [ProfileComponent, TranslateModule.forRoot()],
        providers: [
          { provide: SkillNamesService, useValue: { names$: of(SkillNames.NONE) } },
          provideRouter([]),
          provideNativeDateAdapter(),
          { provide: UserManagementService, useValue: bed.users },
          { provide: NotificationService, useValue: bed.notifications },
          { provide: ThemeService, useValue: bed.theme },
          // The public profile tab has its own spec; here it loads hidden settings.
          { provide: PublicProfileService, useValue: { settings: () => of({ enabled: false, slug: null }) } },
          { provide: AccountSecurityService, useValue: { sessions: () => of([]), activity: () => of({ events: [], total: 0, page: 0, size: 20 }),
          apiTokens: () => of([]) } },
          { provide: WebhookService, useValue: { list: () => of([]) } }
        ]
      });
      bed.navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
      const fixture = TestBed.createComponent(ProfileComponent);
      fixture.detectChanges();
      return fixture;
    },
    text(fixture: ComponentFixture<ProfileComponent>): string {
      fixture.detectChanges();
      return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
    }
  } as ProfileTestBed;

  beforeEach(() => {
    savedLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    bed.consoleError = spyOn(console, 'error');
  });

  afterEach(() => {
    if (savedLanguage === null) {
      localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    } else {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, savedLanguage);
    }
  });

  return bed;
}
