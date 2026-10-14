import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import en from '../../../../assets/i18n/en.json';
import fr from '../../../../assets/i18n/fr.json';
import { LoginResponse } from '../../../models/auth.models';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let notification: jasmine.SpyObj<NotificationService>;
  let navigateByUrl: jasmine.Spy;

  /** Creates the page as opened from `/login`, optionally with the `returnUrl` a guard added. */
  function create(returnUrl?: string): LoginComponent {
    auth = jasmine.createSpyObj('AuthService', ['login', 'isAuthenticated', 'startSsoLogin']);
    notification = jasmine.createSpyObj('NotificationService', ['success', 'info', 'errorText']);
    TestBed.configureTestingModule({
      imports: [LoginComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(returnUrl ? { returnUrl } : {}) } } },
        { provide: AuthService, useValue: auth },
        { provide: NotificationService, useValue: notification }
      ]
    });
    navigateByUrl = spyOn(TestBed.inject(Router), 'navigateByUrl');
    return TestBed.createComponent(LoginComponent).componentInstance;
  }

  function fill(component: LoginComponent, email = 'jean.dupont@example.test', password = 'Password123!'): void {
    component.loginForm.patchValue({ email, password });
    component.loginForm.markAllAsTouched();
  }

  function failWith(error: HttpErrorResponse): void {
    auth.login.and.returnValue(throwError(() => error));
  }

  it('sends a visitor who is already signed in to the dashboard', () => {
    const component = create();
    auth.isAuthenticated.and.returnValue(true);
    component.ngOnInit();
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('stays on the page for a visitor who is not signed in', () => {
    const component = create();
    auth.isAuthenticated.and.returnValue(false);
    component.ngOnInit();
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('signs in with the email and password only and returns to the requested page', () => {
    const component = create('/projects?project=42');
    auth.login.and.returnValue(of({ token: 'jwt' } as LoginResponse));
    fill(component);
    component.onSubmit();
    expect(auth.login).toHaveBeenCalledWith({ email: 'jean.dupont@example.test', password: 'Password123!' });
    expect(notification.success).toHaveBeenCalledWith('auth.loginSuccess');
    expect(navigateByUrl).toHaveBeenCalledWith('/projects?project=42');
    expect(component.isLoading).toBeFalse();
  });

  it('does not send an incomplete form', () => {
    const component = create();
    fill(component, 'not-an-email');
    component.onSubmit();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('asks for the authenticator code when the account requires it, then sends it with the password', () => {
    const component = create();
    failWith(new HttpErrorResponse({ status: 401, error: { twoFactorRequired: true } }));
    fill(component);
    component.onSubmit();
    expect(component.needsTotpCode).toBeTrue();
    expect(notification.info).toHaveBeenCalledWith('auth.totpRequired');
    expect(notification.errorText).not.toHaveBeenCalled();
    expect(component.loginForm.valid).toBeFalse();

    component.loginForm.patchValue({ totpCode: '12345' });
    expect(component.loginForm.valid).toBeFalse();
    component.loginForm.patchValue({ totpCode: '123456' });
    expect(component.loginForm.valid).toBeTrue();
    auth.login.and.returnValue(of({ token: 'jwt' } as LoginResponse));
    component.onSubmit();
    expect(auth.login).toHaveBeenCalledWith({ email: 'jean.dupont@example.test', password: 'Password123!', totpCode: '123456' });
    expect(navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('explains a failed sign-in by its cause', () => {
    const component = create();
    fill(component);
    const messageFor = (status: number): string => {
      failWith(new HttpErrorResponse({ status }));
      component.onSubmit();
      expect(notification.errorText.calls.mostRecent().args[1]).toBe(5000);
      return notification.errorText.calls.mostRecent().args[0];
    };
    expect(messageFor(401)).toBe('auth.invalidCredentials');
    expect(messageFor(429)).toBe('auth.tooManyAttempts');
    expect(messageFor(0)).toBe('auth.serverUnavailable');
    expect(messageFor(500)).toBe('auth.loginError');
    expect(component.needsTotpCode).toBeFalse();
    expect(component.isLoading).toBeFalse();
  });

  it('explains the field errors', () => {
    const component = create();
    fill(component, '', '');
    expect(component.getErrorMessage('email')).toBe('auth.emailRequired');
    expect(component.getErrorMessage('password')).toBe('auth.passwordRequired');
    fill(component, 'not-an-email', 'abc');
    expect(component.getErrorMessage('email')).toBe('common.invalidEmail');
    expect(component.getErrorMessage('password')).toBe('auth.passwordMinLength');
    fill(component);
    expect(component.getErrorMessage('email')).toBe('');
  });

  it('hands the Keycloak sign-in over to the auth service', () => {
    create().onSsoLogin();
    expect(auth.startSsoLogin).toHaveBeenCalled();
  });
});

describe('LoginComponent template', () => {
  it('shows the email placeholder in the current language', async () => {
    await TestBed.configureTestingModule({
      imports: [LoginComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', ['login', 'isAuthenticated']) },
        { provide: NotificationService, useValue: jasmine.createSpyObj('NotificationService', ['success', 'info', 'errorText']) }
      ]
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('en', en);
    translate.use('fr');
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const email = (): HTMLInputElement => fixture.nativeElement.querySelector('input[formControlName="email"]');

    expect(email().placeholder).toBe('votre@email.com');

    translate.use('en');
    fixture.detectChanges();
    expect(email().placeholder).toBe('your@email.com');
  });
});
