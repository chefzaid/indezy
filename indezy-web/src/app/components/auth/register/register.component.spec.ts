import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import en from '../../../../assets/i18n/en.json';
import fr from '../../../../assets/i18n/fr.json';
import { LoginResponse } from '../../../models/auth.models';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { RegisterComponent } from './register.component';

describe('RegisterComponent', () => {
  let auth: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let notification: jasmine.SpyObj<NotificationService>;
  let component: RegisterComponent;
  const translate = {
    instant: (key: string, params?: Record<string, unknown>) => params ? `${key} ${JSON.stringify(params)}` : key
  } as unknown as TranslateService;

  beforeEach(() => {
    auth = jasmine.createSpyObj('AuthService', ['register', 'isAuthenticated']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    notification = jasmine.createSpyObj('NotificationService', ['success', 'errorText']);
    component = new RegisterComponent(new FormBuilder(), auth, router, notification, translate);
  });

  function fill(password: string, confirmPassword = password): void {
    component.registerForm.setValue({
      firstName: 'Jean', lastName: 'Dupont', email: 'jean.dupont@example.test', password, confirmPassword
    });
    component.registerForm.markAllAsTouched();
  }

  it('redirects visitors who are already signed in to the dashboard', () => {
    auth.isAuthenticated.and.returnValue(true);
    component.ngOnInit();
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('limits the password to the 72 bytes the server can hash, counting accented letters as 2', () => {
    fill('é'.repeat(36));
    expect(component.registerForm.valid).toBeTrue();
    expect(component.getErrorMessage('password')).toBe('');

    fill('é'.repeat(37));
    expect(component.registerForm.valid).toBeFalse();
    expect(component.getErrorMessage('password')).toBe('common.passwordMaxBytes {"max":72}');
    component.onSubmit();
    expect(auth.register).not.toHaveBeenCalled();
  });

  it('explains the other field errors', () => {
    fill('abc', 'abd');
    expect(component.getErrorMessage('password')).toBe('common.minChars {"count":6}');
    expect(component.getErrorMessage('confirmPassword')).toBe('common.passwordMismatch');
    component.registerForm.patchValue({ firstName: '', email: 'not-an-email', password: '', confirmPassword: '' });
    expect(component.getErrorMessage('firstName')).toBe('auth.firstNameRequired');
    expect(component.getErrorMessage('password')).toBe('auth.passwordRequired');
    expect(component.getErrorMessage('confirmPassword')).toBe('auth.confirmPasswordRequired');
    expect(component.getErrorMessage('email')).toBe('common.invalidEmail');
    expect(component.getErrorMessage('lastName')).toBe('');
  });

  it('registers the account without the confirmation and opens the dashboard', () => {
    auth.register.and.returnValue(of({ token: 'jwt' } as LoginResponse));
    fill('Password123!');
    component.onSubmit();
    expect(auth.register).toHaveBeenCalledWith({
      firstName: 'Jean', lastName: 'Dupont', email: 'jean.dupont@example.test', password: 'Password123!'
    });
    expect(notification.success).toHaveBeenCalledWith('auth.registerSuccess');
    expect(router.navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.isLoading).toBeFalse();
  });

  it('reports an existing account only for a conflict', () => {
    const failWith = (status: number): string => {
      auth.register.and.returnValue(throwError(() => new HttpErrorResponse({ status })));
      component.onSubmit();
      return notification.errorText.calls.mostRecent().args[0];
    };
    fill('Password123!');
    expect(failWith(409)).toBe('auth.emailExists');
    expect(failWith(400)).toBe('auth.registerError');
    expect(failWith(0)).toBe('auth.serverUnavailable');
    expect(component.isLoading).toBeFalse();
  });
});

describe('RegisterComponent template', () => {
  it('names the password visibility buttons in the current language and reports their state', async () => {
    await TestBed.configureTestingModule({
      imports: [RegisterComponent, RouterModule.forRoot([]), TranslateModule.forRoot()],
      providers: [
        { provide: AuthService, useValue: jasmine.createSpyObj('AuthService', ['register', 'isAuthenticated']) },
        { provide: NotificationService, useValue: jasmine.createSpyObj('NotificationService', ['success', 'errorText']) }
      ]
    }).compileComponents();
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.setTranslation('en', en);
    translate.use('fr');
    const fixture = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    const toggles = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('button[aria-pressed]'));
    const labels = (): (string | null)[] => toggles().map(button => button.getAttribute('aria-label'));

    expect(labels()).toEqual(['Masquer le mot de passe', 'Masquer la confirmation du mot de passe']);

    translate.use('en');
    fixture.detectChanges();
    expect(labels()).toEqual(['Hide password', 'Hide password confirmation']);

    // The name stays the same while aria-pressed reports whether the password is hidden, as on the login page.
    toggles()[0].click();
    fixture.detectChanges();
    expect(toggles().map(button => button.getAttribute('aria-pressed'))).toEqual(['false', 'true']);
    expect(fixture.nativeElement.querySelector('input[formControlName="password"]').type).toBe('text');
  });
});
