import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { UserNotificationSettings, UserPreferences, UserProfile } from '../../models/user-management.models';
import { NotificationService } from '../../services/notification/notification.service';
import { PublicProfileService } from '../../services/public-profile/public-profile.service';
import { AccountSecurityService } from '../../services/account-security/account-security.service';
import { TwoFactorSetup, UserManagementService } from '../../services/user-management/user-management.service';
import { ThemeService } from '../../shared/theme/theme.service';
import { ProfileComponent } from './profile.component';

/** Opens a tab of the profile page and waits until its content is attached. */
async function selectTab(fixture: ComponentFixture<ProfileComponent>, index: number, marker: string): Promise<void> {
  const element = fixture.nativeElement as HTMLElement;
  element.querySelectorAll<HTMLElement>('[role="tab"]')[index].click();
  for (let attempt = 0; attempt < 50 && !element.querySelector('mat-tab-body.mat-mdc-tab-body-active')?.textContent!.includes(marker);
    attempt++) {
    fixture.detectChanges();
    await fixture.whenStable();
    await new Promise(resolve => setTimeout(resolve, 10));
  }
  fixture.detectChanges();
}

/** The security tab of the profile page: password change and two-factor authentication. */
describe('ProfileComponent security', () => {
  let users: jasmine.SpyObj<UserManagementService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let fixture: ComponentFixture<ProfileComponent>;
  let component: ProfileComponent;

  const setup: TwoFactorSetup = { secret: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/Indezy?secret=JBSWY3DPEHPK3PXP' };

  async function create(twoFactorEnabled = false): Promise<void> {
    users = jasmine.createSpyObj('UserManagementService', ['getUserProfile', 'getUserPreferences', 'getNotificationSettings',
      'getSecuritySettings', 'changePassword', 'enableTwoFactor', 'verifyTwoFactor', 'disableTwoFactor']);
    users.getUserProfile.and.returnValue(of({ id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' } as UserProfile));
    users.getUserPreferences.and.returnValue(of({ theme: 'light', language: 'fr' } as UserPreferences));
    users.getNotificationSettings.and.returnValue(of({} as UserNotificationSettings));
    users.getSecuritySettings.and.returnValue(of({ twoFactorEnabled, lastPasswordChange: new Date() }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    spyOn(console, 'error');
    TestBed.configureTestingModule({
      imports: [ProfileComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications },
        { provide: ThemeService, useValue: jasmine.createSpyObj('ThemeService', ['apply']) },
        // The public profile tab has its own spec; here it loads hidden settings.
        { provide: PublicProfileService, useValue: { settings: () => of({ enabled: false, slug: null }) } },
        { provide: AccountSecurityService, useValue: { sessions: () => of([]), activity: () => of({ events: [], total: 0, page: 0, size: 20 }),
          apiTokens: () => of([]) } }
      ]
    });
    fixture = TestBed.createComponent(ProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await selectTab(fixture, 1, 'profile.currentPassword');
  }

  function element(): HTMLElement {
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  function text(): string {
    return element().textContent!.replace(/\s+/g, ' ');
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(element().querySelectorAll<HTMLButtonElement>('mat-tab-body.mat-mdc-tab-body-active button'))
      .find(candidate => candidate.textContent!.includes(label))!;
  }

  function fillPassword(currentPassword: string, newPassword: string, confirmPassword: string): void {
    component.passwordForm.setValue({ currentPassword, newPassword, confirmPassword });
  }

  describe('password change', () => {
    it('flags a confirmation that differs from the new password until it matches', async () => {
      await create();
      fillPassword('old-secret', 'new-secret-1', 'new-secret-2');
      component.passwordForm.markAllAsTouched();

      expect(component.passwordForm.hasError('passwordMismatch')).toBeTrue();
      expect(text()).toContain('profile.errors.passwordMismatch');
      expect(button('profile.changePassword').disabled).toBeTrue();

      component.passwordForm.patchValue({ confirmPassword: 'new-secret-1' });

      expect(component.passwordForm.valid).toBeTrue();
      expect(component.passwordForm.get('confirmPassword')!.errors).toBeNull();
      expect(text()).not.toContain('profile.errors.passwordMismatch');
      expect(button('profile.changePassword').disabled).toBeFalse();
    });

    it('keeps an empty confirmation required while it differs from the new password', async () => {
      await create();
      fillPassword('old-secret', 'new-secret-1', '');

      const confirmation = component.passwordForm.get('confirmPassword')!;
      expect(confirmation.hasError('required')).toBeTrue();
      expect(confirmation.hasError('passwordMismatch')).toBeTrue();

      fillPassword('old-secret', '', '');

      expect(confirmation.hasError('required')).toBeTrue();
      expect(confirmation.hasError('passwordMismatch')).toBeFalse();
    });

    it('keeps the other errors of the confirmation when the passwords match again', async () => {
      await create();
      fillPassword('old-secret', 'new-secret-1', 'new-secret-2');
      const confirm = component.passwordForm.get('confirmPassword')!;
      confirm.setErrors({ ...confirm.errors, incorrect: true });

      component.passwordForm.get('newPassword')!.setValue('new-secret-2');

      expect(confirm.errors).toEqual({ incorrect: true });
    });

    it('explains missing and too short passwords', async () => {
      await create();
      component.passwordForm.markAllAsTouched();

      expect(text()).toContain('profile.errors.currentPasswordRequired');
      expect(text()).toContain('profile.errors.newPasswordRequired');

      component.passwordForm.patchValue({ newPassword: 'short' });
      expect(text()).toContain('profile.errors.passwordMinLength');

      component.passwordForm.patchValue({ newPassword: 'é'.repeat(40) });
      expect(text()).toContain('common.passwordMaxBytes');
    });

    it('changes the password, then clears the form', async () => {
      await create();
      users.changePassword.and.returnValue(of(true));
      fillPassword('old-secret', 'new-secret-1', 'new-secret-1');

      button('profile.changePassword').click();

      expect(users.changePassword).toHaveBeenCalledWith({
        currentPassword: 'old-secret', newPassword: 'new-secret-1', confirmPassword: 'new-secret-1'
      });
      expect(notifications.success).toHaveBeenCalledWith('profile.passwordChanged');
      expect(component.passwordForm.value).toEqual({ currentPassword: null, newPassword: null, confirmPassword: null });
      expect(component.isUpdating).toBeFalse();
    });

    it('does not send an incomplete form', async () => {
      await create();
      fillPassword('', 'new-secret-1', 'new-secret-1');

      component.onChangePassword();

      expect(users.changePassword).not.toHaveBeenCalled();
    });

    it('shows the server rejecting the current password on its field', async () => {
      await create();
      users.changePassword.and.returnValue(throwError(() => new HttpErrorResponse({
        status: 400, error: { validationErrors: { currentPassword: 'Current password is incorrect' } }
      })));
      fillPassword('wrong-secret', 'new-secret-1', 'new-secret-1');

      component.onChangePassword();

      const current = component.passwordForm.get('currentPassword')!;
      expect(current.hasError('incorrect')).toBeTrue();
      expect(current.touched).toBeTrue();
      expect(text()).toContain('profile.errors.currentPasswordIncorrect');
      expect(notifications.error).not.toHaveBeenCalled();
      expect(component.isUpdating).toBeFalse();
    });

    it('shows the server rejecting the confirmation on its field', async () => {
      await create();
      users.changePassword.and.returnValue(throwError(() => new HttpErrorResponse({
        status: 400, error: { validationErrors: { confirmPassword: 'New password and confirmation do not match' } }
      })));
      fillPassword('old-secret', 'new-secret-1', 'new-secret-1');

      component.onChangePassword();

      expect(component.passwordForm.get('confirmPassword')!.hasError('passwordMismatch')).toBeTrue();
      expect(notifications.error).not.toHaveBeenCalled();
    });

    it('reports any other failure', async () => {
      await create();
      users.changePassword.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      fillPassword('old-secret', 'new-secret-1', 'new-secret-1');

      component.onChangePassword();

      expect(notifications.error).toHaveBeenCalledWith('profile.errors.changingPassword');
      expect(component.isUpdating).toBeFalse();
    });

    it('shows the progress while the change is in flight', async () => {
      await create();
      fillPassword('old-secret', 'new-secret-1', 'new-secret-1');
      component.isUpdating = true;

      expect(button('profile.changing').disabled).toBeTrue();
    });
  });

  describe('two-factor authentication', () => {
    it('walks through the setup and enables it with a valid code', async () => {
      await create();
      users.enableTwoFactor.and.returnValue(of(setup));
      users.verifyTwoFactor.and.returnValue(of(true));
      expect(text()).toContain('profile.twoFactor.statusDisabled');

      button('profile.twoFactor.enable').click();

      expect(text()).toContain('JBSWY3DPEHPK3PXP');
      expect(button('profile.twoFactor.verify').disabled).toBeTrue();
      component.twoFactorCode = ' 123456 ';
      button('profile.twoFactor.verify').click();

      expect(users.verifyTwoFactor).toHaveBeenCalledWith('123456');
      expect(notifications.success).toHaveBeenCalledWith('profile.twoFactor.enabled');
      expect(component.twoFactorEnabled).toBeTrue();
      expect(component.twoFactorSetup).toBeNull();
      expect(component.twoFactorCode).toBe('');
      expect(text()).toContain('profile.twoFactor.statusEnabled');
    });

    it('reports a setup that cannot start', async () => {
      await create();
      users.enableTwoFactor.and.returnValue(throwError(() => new Error('offline')));

      component.startTwoFactorSetup();

      expect(notifications.error).toHaveBeenCalledWith('profile.twoFactor.error');
      expect(component.isProcessingTwoFactor).toBeFalse();
      expect(component.twoFactorSetup).toBeNull();
    });

    it('keeps the setup open when the code is wrong', async () => {
      await create();
      component.twoFactorSetup = setup;
      component.twoFactorCode = '000000';
      users.verifyTwoFactor.and.returnValue(of(false));

      component.verifyTwoFactorSetup();

      expect(notifications.error).toHaveBeenCalledWith('profile.twoFactor.invalidCode');
      expect(component.twoFactorEnabled).toBeFalse();
      expect(component.twoFactorSetup).toBe(setup);
      expect(component.isProcessingTwoFactor).toBeFalse();
    });

    it('reports a verification that fails', async () => {
      await create();
      component.twoFactorCode = '123456';
      users.verifyTwoFactor.and.returnValue(throwError(() => new Error('offline')));

      component.verifyTwoFactorSetup();

      expect(notifications.error).toHaveBeenCalledWith('profile.twoFactor.error');
      expect(component.isProcessingTwoFactor).toBeFalse();
    });

    it('does not verify or disable without a code', async () => {
      await create();
      component.twoFactorCode = '   ';

      component.verifyTwoFactorSetup();
      component.disableTwoFactor();

      expect(users.verifyTwoFactor).not.toHaveBeenCalled();
      expect(users.disableTwoFactor).not.toHaveBeenCalled();
    });

    it('cancels the setup', async () => {
      await create();
      component.twoFactorSetup = setup;
      component.twoFactorCode = '12';

      button('common.cancel').click();

      expect(component.twoFactorSetup).toBeNull();
      expect(component.twoFactorCode).toBe('');
      expect(button('profile.twoFactor.enable')).toBeDefined();
    });

    it('disables it with a current code', async () => {
      await create(true);
      users.disableTwoFactor.and.returnValue(of(true));
      expect(text()).toContain('profile.twoFactor.statusEnabled');
      expect(button('profile.twoFactor.disable').disabled).toBeTrue();

      component.twoFactorCode = '654321';
      button('profile.twoFactor.disable').click();

      expect(users.disableTwoFactor).toHaveBeenCalledWith('654321');
      expect(notifications.success).toHaveBeenCalledWith('profile.twoFactor.disabled');
      expect(component.twoFactorEnabled).toBeFalse();
      expect(component.twoFactorCode).toBe('');
    });

    it('keeps it on when the code is refused', async () => {
      await create(true);
      users.disableTwoFactor.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400 })));
      component.twoFactorCode = '000000';

      component.disableTwoFactor();

      expect(notifications.error).toHaveBeenCalledWith('profile.twoFactor.invalidCode');
      expect(component.twoFactorEnabled).toBeTrue();
      expect(component.isProcessingTwoFactor).toBeFalse();
    });
  });
});
