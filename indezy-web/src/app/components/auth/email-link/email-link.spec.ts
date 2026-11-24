import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ForgotPasswordComponent } from './forgot-password.component';
import { ResetPasswordComponent } from './reset-password.component';
import { VerifyEmailComponent } from './verify-email.component';

describe('Email link pages', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function configure(token: string | null): void {
    TestBed.resetTestingModule();
    auth = jasmine.createSpyObj('AuthService', ['forgotPassword', 'resetPassword', 'verifyEmail']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notifications },
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(token ? { token } : {}) } } }]
    });
    spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
  }

  it('asks for a reset link and says it went, whether an account exists or not', () => {
    configure(null);
    auth.forgotPassword.and.returnValue(of(undefined));
    const fixture = TestBed.createComponent(ForgotPasswordComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ email: 'lea@example.com' });
    fixture.componentInstance.send();
    fixture.detectChanges();
    expect(auth.forgotPassword).toHaveBeenCalledWith('lea@example.com');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-forgot-sent]')).not.toBeNull();
  });

  it('sets the new password typed twice, and says when the link expired', () => {
    configure('abc');
    auth.resetPassword.and.returnValue(of({ token: 't', user: { id: 1, email: 'lea@example.com', firstName: 'Léa', lastName: 'M' } }));
    const fixture = TestBed.createComponent(ResetPasswordComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ password: 'NewPassword1', confirm: 'Different1' });
    expect(fixture.componentInstance.form.hasError('mismatch')).toBeTrue();
    fixture.componentInstance.form.setValue({ password: 'NewPassword1', confirm: 'NewPassword1' });
    fixture.componentInstance.save();
    expect(auth.resetPassword).toHaveBeenCalledWith('abc', 'NewPassword1');
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/dashboard');

    auth.resetPassword.and.returnValue(throwError(() => ({ status: 400 })));
    fixture.componentInstance.save();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-reset-expired]')).not.toBeNull();
  });

  it('confirms the email from its link and opens the account, or says the link no longer works', () => {
    configure('xyz');
    auth.verifyEmail.and.returnValue(of({ token: 't', user: { id: 1, email: 'lea@example.com', firstName: 'Léa', lastName: 'M' } }));
    TestBed.createComponent(VerifyEmailComponent).detectChanges();
    expect(auth.verifyEmail).toHaveBeenCalledWith('xyz');
    expect(TestBed.inject(Router).navigateByUrl).toHaveBeenCalledWith('/dashboard');

    configure('used');
    auth.verifyEmail.and.returnValue(throwError(() => ({ status: 400 })));
    const failed = TestBed.createComponent(VerifyEmailComponent);
    failed.detectChanges();
    expect((failed.nativeElement as HTMLElement).querySelector('[data-verify-failed]')).not.toBeNull();
  });
});
