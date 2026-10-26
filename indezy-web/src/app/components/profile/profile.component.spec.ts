import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, Subject, of, throwError } from 'rxjs';
import { UserNotificationSettings, UserPreferences, UserProfile } from '../../models/user-management.models';
import { NotificationService } from '../../services/notification/notification.service';
import { UserManagementService } from '../../services/user-management/user-management.service';
import { LANGUAGE_STORAGE_KEY } from '../../shared/locale/app-locale';
import { ThemeService } from '../../shared/theme/theme.service';
import { ProfileComponent } from './profile.component';

describe('ProfileComponent', () => {
  let users: jasmine.SpyObj<UserManagementService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let theme: jasmine.SpyObj<ThemeService>;
  let navigate: jasmine.Spy;
  let consoleError: jasmine.Spy;
  let savedLanguage: string | null;

  const profile: UserProfile = {
    id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', phone: '0600000000', birthDate: '1990-05-12',
    city: 'Paris', position: 'Tech lead', skills: ['Java', 'Angular'], languages: ['Anglais'], targetDailyRate: 650,
    availabilityDate: '2026-11-02', timezone: 'Europe/Paris', currency: 'EUR'
  };

  const preferences = {
    theme: 'dark', language: 'fr', dateFormat: 'dd/MM/yyyy', timeFormat: '24h', currency: 'EUR', timezone: 'Europe/Paris',
    defaultView: 'projects', itemsPerPage: '50', autoSave: false, inactivityDays: 30
  } as unknown as UserPreferences;

  const notificationSettings: UserNotificationSettings = {
    emailNotifications: false, pushNotifications: true, projectUpdates: true, clientMessages: false, systemAlerts: true,
    weeklyReports: true, marketingEmails: false
  };

  beforeEach(() => {
    savedLanguage = localStorage.getItem(LANGUAGE_STORAGE_KEY);
    consoleError = spyOn(console, 'error');
  });

  afterEach(() => {
    if (savedLanguage === null) {
      localStorage.removeItem(LANGUAGE_STORAGE_KEY);
    } else {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, savedLanguage);
    }
  });

  function create(loaded: Observable<UserProfile> = of(profile),
                  prefs: UserPreferences = preferences): ComponentFixture<ProfileComponent> {
    users = jasmine.createSpyObj('UserManagementService', ['getUserProfile', 'updateUserProfile', 'uploadAvatar', 'changePassword',
      'getUserPreferences', 'updateUserPreferences', 'getNotificationSettings', 'updateNotificationSettings', 'getSecuritySettings',
      'enableTwoFactor', 'verifyTwoFactor', 'disableTwoFactor', 'exportUserData']);
    users.getUserProfile.and.returnValue(loaded);
    users.getUserPreferences.and.returnValue(of(prefs));
    users.getNotificationSettings.and.returnValue(of(notificationSettings));
    users.getSecuritySettings.and.returnValue(of({ twoFactorEnabled: false, lastPasswordChange: new Date() }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error', 'successWithAction']);
    theme = jasmine.createSpyObj('ThemeService', ['apply']);
    TestBed.configureTestingModule({
      imports: [ProfileComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications },
        { provide: ThemeService, useValue: theme }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const fixture = TestBed.createComponent(ProfileComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProfileComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  describe('loading', () => {
    it('fills every form from the profile, its preferences and its notification settings', () => {
      const component = create().componentInstance;

      expect(component.userProfile).toEqual(profile);
      expect(component.profileForm.value).toEqual(jasmine.objectContaining({
        firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com', city: 'Paris', position: 'Tech lead',
        skills: ['Java', 'Angular'], languages: ['Anglais'], targetDailyRate: 650
      }));
      expect(component.profileForm.value.birthDate).toEqual(new Date('1990-05-12'));
      expect(component.profileForm.value.availabilityDate).toEqual(new Date(2026, 10, 2));
      expect(component.preferencesForm.value).toEqual(jasmine.objectContaining({
        theme: 'dark', dateFormat: 'DD/MM/YYYY', itemsPerPage: 50, defaultView: 'projects', autoSave: false, inactivityDays: 30
      }));
      expect(component.notificationForm.value).toEqual(notificationSettings);
      expect(component.isLoading).toBeFalse();
    });

    it('defaults missing preferences and profile lists', () => {
      const component = create(of({ id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' }),
        { theme: 'light', language: 'fr' } as UserPreferences).componentInstance;

      expect(component.profileForm.value).toEqual(jasmine.objectContaining({
        birthDate: null, availabilityDate: null, targetDailyRate: null, skills: [], languages: []
      }));
      expect(component.preferencesForm.value.dateFormat).toBe('DD/MM/YYYY');
      expect(component.preferencesForm.value.itemsPerPage).toBe(25);
    });

    it('shows a spinner until the profile arrives, then the personal information tab', () => {
      const loaded = new Subject<UserProfile>();
      const fixture = create(loaded);

      expect(text(fixture)).toContain('profile.loading');
      expect(fixture.nativeElement.querySelector('app-profile-personal-info')).toBeNull();

      loaded.next(profile);

      expect(text(fixture)).not.toContain('profile.loading');
      expect(text(fixture)).toContain('Ada Lovelace');
      expect(text(fixture)).toContain('profile.tabs.personalInfo');
    });

    it('reports a profile that cannot be loaded', () => {
      const fixture = create(throwError(() => new Error('offline')));

      expect(notifications.error).toHaveBeenCalledWith('profile.errors.loadingProfile');
      expect(consoleError).toHaveBeenCalled();
      expect(fixture.componentInstance.isLoading).toBeFalse();
      expect(fixture.nativeElement.querySelector('.profile-content')).toBeNull();
    });

    it('reads whether two-factor authentication is on, and keeps it off when that fails', () => {
      const component = create().componentInstance;
      expect(component.twoFactorEnabled).toBeFalse();

      users.getSecuritySettings.and.returnValue(of({ twoFactorEnabled: true, lastPasswordChange: new Date() }));
      component.ngOnInit();
      expect(component.twoFactorEnabled).toBeTrue();

      component.twoFactorEnabled = false;
      users.getSecuritySettings.and.returnValue(throwError(() => new Error('offline')));
      component.ngOnInit();
      expect(component.twoFactorEnabled).toBeFalse();
    });
  });

  describe('personal information', () => {
    it('saves the profile with ISO dates and confirms when the suggestion inputs did not change', () => {
      const component = create().componentInstance;
      users.updateUserProfile.and.returnValue(of({ ...profile, skills: ['Angular', 'Java'], phone: '0700000000' }));
      component.profileForm.patchValue({ phone: '0700000000', availabilityDate: new Date(2026, 11, 1) });

      component.onUpdateProfile();

      expect(users.updateUserProfile).toHaveBeenCalledWith(jasmine.objectContaining({
        phone: '0700000000', birthDate: '1990-05-12', availabilityDate: '2026-12-01'
      }));
      expect(notifications.success).toHaveBeenCalledWith('profile.updateSuccess');
      expect(notifications.successWithAction).not.toHaveBeenCalled();
      expect(component.isUpdating).toBeFalse();
      expect(component.userProfile!.phone).toBe('0700000000');
    });

    it('sends empty dates as null', () => {
      const component = create().componentInstance;
      users.updateUserProfile.and.returnValue(of(profile));
      component.profileForm.patchValue({ birthDate: null, availabilityDate: null });

      component.onUpdateProfile();

      expect(users.updateUserProfile).toHaveBeenCalledWith(jasmine.objectContaining({ birthDate: null, availabilityDate: null }));
    });

    it('offers to see the new search suggestions when the job title changed', () => {
      const component = create().componentInstance;
      const action = new Subject<void>();
      notifications.successWithAction.and.returnValue(action);
      users.updateUserProfile.and.returnValue(of({ ...profile, position: 'Architect' }));

      component.onUpdateProfile();

      expect(notifications.successWithAction).toHaveBeenCalledWith('profile.updateSuccessSuggestions', 'profile.seeSuggestions', 8000);
      expect(notifications.success).not.toHaveBeenCalled();
      expect(navigate).not.toHaveBeenCalled();

      action.next();
      expect(navigate).toHaveBeenCalledWith(['/offers'], { state: { openSuggestions: true } });
    });

    it('treats a changed city or daily rate as new suggestion inputs', () => {
      const component = create().componentInstance;
      notifications.successWithAction.and.returnValue(new Subject<void>());

      users.updateUserProfile.and.returnValue(of({ ...profile, city: 'Lyon' }));
      component.onUpdateProfile();
      users.updateUserProfile.and.returnValue(of({ ...profile, city: 'Lyon', targetDailyRate: 700 }));
      component.onUpdateProfile();

      expect(notifications.successWithAction).toHaveBeenCalledTimes(2);
    });

    it('does not save an invalid profile', () => {
      const component = create().componentInstance;
      component.profileForm.patchValue({ email: 'not-an-email' });

      component.onUpdateProfile();

      expect(users.updateUserProfile).not.toHaveBeenCalled();
    });

    it('reports a profile that cannot be saved', () => {
      const component = create().componentInstance;
      users.updateUserProfile.and.returnValue(throwError(() => new Error('offline')));

      component.onUpdateProfile();

      expect(notifications.error).toHaveBeenCalledWith('profile.errors.updatingProfile');
      expect(component.isUpdating).toBeFalse();
    });

    it('saves when the personal information form is submitted', () => {
      const fixture = create();
      users.updateUserProfile.and.returnValue(of(profile));

      (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('app-profile-personal-info button[type="submit"]')!.click();

      expect(users.updateUserProfile).toHaveBeenCalled();
    });
  });

  describe('avatar', () => {
    function upload(component: ProfileComponent, file?: File): HTMLInputElement {
      const input = document.createElement('input');
      input.type = 'file';
      if (file) {
        const transfer = new DataTransfer();
        transfer.items.add(file);
        input.files = transfer.files;
      }
      component.onAvatarUpload({ target: input } as unknown as Event);
      return input;
    }

    it('ignores an empty selection', () => {
      const component = create().componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage');

      upload(component);

      expect(downscale).not.toHaveBeenCalled();
      expect(notifications.error).not.toHaveBeenCalled();
    });

    it('refuses files that are not images or are too large', () => {
      const component = create().componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage');

      const input = upload(component, new File(['x'], 'cv.pdf', { type: 'application/pdf' }));
      expect(input.value).toBe('');
      expect(notifications.error).toHaveBeenCalledWith('profile.errors.selectImage');

      const large = new File(['x'], 'photo.png', { type: 'image/png' });
      Object.defineProperty(large, 'size', { value: 10 * 1024 * 1024 + 1 });
      upload(component, large);
      expect(notifications.error).toHaveBeenCalledWith('profile.errors.imageTooLarge');
      expect(downscale).not.toHaveBeenCalled();
      expect(component.isUpdating).toBeFalse();
    });

    it('uploads the downscaled image and shows it', async () => {
      const component = create().componentInstance;
      const scaled = new Blob(['jpeg'], { type: 'image/jpeg' });
      const downscale = spyOn(ProfileComponent, 'downscaleImage').and.resolveTo(scaled);
      users.uploadAvatar.and.returnValue(of('data:image/jpeg;base64,AAAA'));
      const file = new File(['png'], 'photo.png', { type: 'image/png' });

      upload(component, file);
      expect(component.isUpdating).toBeTrue();
      await downscale.calls.mostRecent().returnValue;

      expect(downscale).toHaveBeenCalledWith(file, ProfileComponent.AVATAR_SIZE);
      expect(users.uploadAvatar).toHaveBeenCalledWith(scaled);
      expect(component.userProfile!.avatar).toBe('data:image/jpeg;base64,AAAA');
      expect(component.isUpdating).toBeFalse();
      expect(notifications.success).toHaveBeenCalledWith('profile.avatarUpdated');
    });

    it('confirms the upload even before a profile was loaded', async () => {
      const component = create(throwError(() => new Error('offline'))).componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage').and.resolveTo(new Blob(['jpeg']));
      users.uploadAvatar.and.returnValue(of('data:image/jpeg;base64,AAAA'));

      upload(component, new File(['png'], 'photo.png', { type: 'image/png' }));
      await downscale.calls.mostRecent().returnValue;

      expect(component.userProfile).toBeNull();
      expect(notifications.success).toHaveBeenCalledWith('profile.avatarUpdated');
    });

    it('reports an upload that fails', async () => {
      const component = create().componentInstance;
      const downscale = spyOn(ProfileComponent, 'downscaleImage').and.resolveTo(new Blob(['jpeg']));
      users.uploadAvatar.and.returnValue(throwError(() => new Error('too large')));

      upload(component, new File(['png'], 'photo.png', { type: 'image/png' }));
      await downscale.calls.mostRecent().returnValue;

      expect(notifications.error).toHaveBeenCalledWith('profile.errors.uploadingAvatar');
      expect(component.isUpdating).toBeFalse();
      expect(component.userProfile!.avatar).toBeUndefined();
    });

    it('reports an image that cannot be read', async () => {
      const component = create().componentInstance;
      spyOn(ProfileComponent, 'downscaleImage').and.rejectWith(new Error('Unreadable image'));

      upload(component, new File(['png'], 'photo.png', { type: 'image/png' }));
      await new Promise(resolve => setTimeout(resolve));

      expect(users.uploadAvatar).not.toHaveBeenCalled();
      expect(notifications.error).toHaveBeenCalledWith('profile.errors.selectImage');
      expect(component.isUpdating).toBeFalse();
    });

    it('center-crops an image to a small JPEG square', async () => {
      const canvas = document.createElement('canvas');
      canvas.width = 40;
      canvas.height = 20;
      canvas.getContext('2d')!.fillRect(0, 0, 40, 20);
      const png = await new Promise<Blob>(resolve => canvas.toBlob(blob => resolve(blob!), 'image/png'));

      const scaled = await ProfileComponent.downscaleImage(png, 16);

      expect(scaled.type).toBe('image/jpeg');
      const bitmap = await createImageBitmap(scaled);
      expect([bitmap.width, bitmap.height]).toEqual([16, 16]);
    });

    it('rejects data that is not an image', async () => {
      await expectAsync(ProfileComponent.downscaleImage(new Blob(['not an image'], { type: 'image/png' }), 16))
        .toBeRejectedWithError('Unreadable image');
    });
  });

  describe('preferences', () => {
    it('saves the preferences and applies the theme', () => {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
      const component = create().componentInstance;
      const reload = spyOn(component, 'reloadPage');
      users.updateUserPreferences.and.returnValue(of(preferences));

      component.onUpdatePreferences();

      expect(users.updateUserPreferences).toHaveBeenCalledWith(jasmine.objectContaining({ theme: 'dark', itemsPerPage: 50 }));
      expect(notifications.success).toHaveBeenCalledWith('profile.preferencesUpdated');
      expect(theme.apply).toHaveBeenCalledWith('dark');
      expect(component.isUpdating).toBeFalse();
      expect(reload).not.toHaveBeenCalled();
    });

    it('switches to a newly preferred language with a page reload', fakeAsync(() => {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
      const component = create().componentInstance;
      const reload = spyOn(component, 'reloadPage');
      users.updateUserPreferences.and.returnValue(of(preferences));
      component.preferencesForm.patchValue({ language: 'en' });

      component.onUpdatePreferences();

      expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en');
      tick(799);
      expect(reload).not.toHaveBeenCalled();
      tick(1);
      expect(reload).toHaveBeenCalledTimes(1);
    }));

    it('ignores a language the application does not offer', () => {
      localStorage.setItem(LANGUAGE_STORAGE_KEY, 'fr');
      const component = create().componentInstance;
      users.updateUserPreferences.and.returnValue(of(preferences));
      component.preferencesForm.patchValue({ language: 'de' });

      component.onUpdatePreferences();

      expect(localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('fr');
    });

    it('does not save without a valid inactivity delay', () => {
      const component = create().componentInstance;
      component.preferencesForm.patchValue({ inactivityDays: 0 });

      component.onUpdatePreferences();

      expect(users.updateUserPreferences).not.toHaveBeenCalled();
    });

    it('reports preferences that cannot be saved', () => {
      const component = create().componentInstance;
      users.updateUserPreferences.and.returnValue(throwError(() => new Error('offline')));

      component.onUpdatePreferences();

      expect(notifications.error).toHaveBeenCalledWith('profile.errors.updatingPreferences');
      expect(theme.apply).not.toHaveBeenCalled();
      expect(component.isUpdating).toBeFalse();
    });
  });

  describe('notifications', () => {
    it('saves the notification settings', () => {
      const component = create().componentInstance;
      users.updateNotificationSettings.and.returnValue(of(notificationSettings));
      component.notificationForm.patchValue({ marketingEmails: true });

      component.onUpdateNotifications();

      expect(users.updateNotificationSettings).toHaveBeenCalledWith({ ...notificationSettings, marketingEmails: true });
      expect(notifications.success).toHaveBeenCalledWith('profile.notificationsUpdated');
      expect(component.isUpdating).toBeFalse();
    });

    it('reports notification settings that cannot be saved', () => {
      const component = create().componentInstance;
      users.updateNotificationSettings.and.returnValue(throwError(() => new Error('offline')));

      component.onUpdateNotifications();

      expect(notifications.error).toHaveBeenCalledWith('profile.errors.updatingNotifications');
      expect(component.isUpdating).toBeFalse();
    });

    it('does not save when the form is invalid', () => {
      const component = create().componentInstance;
      component.notificationForm.setErrors({ invalid: true });

      component.onUpdateNotifications();

      expect(users.updateNotificationSettings).not.toHaveBeenCalled();
    });
  });

  describe('data export', () => {
    it('downloads the exported data as JSON', () => {
      const component = create().componentInstance;
      const blob = new Blob(['{}'], { type: 'application/json' });
      users.exportUserData.and.returnValue(of(blob));
      const createUrl = spyOn(URL, 'createObjectURL').and.returnValue('blob:export');
      spyOn(URL, 'revokeObjectURL');
      const click = spyOn(HTMLAnchorElement.prototype, 'click');

      component.onExportData();

      expect(createUrl).toHaveBeenCalledWith(blob);
      expect(click).toHaveBeenCalled();
      expect((click.calls.mostRecent().object as HTMLAnchorElement).download).toBe('indezy-user-data.json');
      expect(notifications.success).toHaveBeenCalledWith('profile.dataExported');
    });

    it('reports an export that fails', () => {
      const component = create().componentInstance;
      users.exportUserData.and.returnValue(throwError(() => new Error('offline')));

      component.onExportData();

      expect(notifications.error).toHaveBeenCalledWith('profile.errors.exportingData');
    });

    it('exports from the account tab', async () => {
      const fixture = create();
      users.exportUserData.and.returnValue(throwError(() => new Error('offline')));
      const element = fixture.nativeElement as HTMLElement;
      element.querySelectorAll<HTMLElement>('[role="tab"]')[5].click();
      for (let attempt = 0; attempt < 50 && !element.querySelector('mat-tab-body.mat-mdc-tab-body-active')?.textContent!
        .includes('common.export'); attempt++) {
        fixture.detectChanges();
        await fixture.whenStable();
        await new Promise(resolve => setTimeout(resolve, 10));
      }

      const button = Array.from(element.querySelectorAll<HTMLButtonElement>('mat-tab-body.mat-mdc-tab-body-active button'))
        .find(candidate => candidate.textContent!.includes('common.export'))!;
      button.click();

      expect(users.exportUserData).toHaveBeenCalled();
    });
  });
});
