import { Component, OnInit, ChangeDetectionStrategy } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { environment } from '../../../../environments/environment';

@Component({
    selector: 'app-login',
    imports: [
    ReactiveFormsModule,
    RouterModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatSnackBarModule,
    MatProgressSpinnerModule,
    TranslateModule
],
    templateUrl: './login.component.html',
    changeDetection: ChangeDetectionStrategy.Eager,
    styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  isLoading = false;
  hidePassword = true;
  /** Set when the API accepted the password but asks for the authenticator code. */
  needsTotpCode = false;
  readonly ssoEnabled = environment.production;

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router,
    private readonly notificationService: NotificationService,
    private readonly translateService: TranslateService
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      totpCode: ['']
    });
  }

  ngOnInit(): void {
    // Redirect if already authenticated
    if (this.authService.isAuthenticated()) {
      this.router.navigate(['/dashboard']);
    }
  }

  onSubmit(): void {
    if (this.loginForm.valid) {
      this.isLoading = true;
      const { totpCode, ...credentials } = this.loginForm.value;
      if (this.needsTotpCode) {
        credentials.totpCode = totpCode;
      }

      this.authService.login(credentials).subscribe({
        next: () => {
          this.isLoading = false;
          this.notificationService.success('auth.loginSuccess');
          this.router.navigate(['/dashboard']);
        },
        error: (error) => {
          this.isLoading = false;
          let errorMessage = this.translateService.instant('auth.loginError');
          
          if (error.status === 401 && error.error?.twoFactorRequired) {
            this.needsTotpCode = true;
            this.loginForm.get('totpCode')?.setValidators([Validators.required, Validators.pattern(/^\s*\d{6}\s*$/)]);
            this.loginForm.get('totpCode')?.updateValueAndValidity();
            this.notificationService.info('auth.totpRequired');
            return;
          }
          if (error.status === 401) {
            errorMessage = this.translateService.instant('auth.invalidCredentials');
          } else if (error.status === 429) {
            errorMessage = this.translateService.instant('auth.tooManyAttempts');
          } else if (error.status === 0) {
            errorMessage = this.translateService.instant('auth.serverUnavailable');
          }

          this.notificationService.errorText(errorMessage, 5000);
        }
      });
    }
  }

  onSsoLogin(): void {
    this.authService.startSsoLogin();
  }

  getErrorMessage(field: string): string {
    const control = this.loginForm.get(field);
    if (control?.hasError('required')) {
      return this.translateService.instant(field === 'email' ? 'auth.emailRequired' : 'auth.passwordRequired');
    }
    if (control?.hasError('email')) {
      return this.translateService.instant('common.invalidEmail');
    }
    if (control?.hasError('minlength')) {
      return this.translateService.instant('auth.passwordMinLength');
    }
    return '';
  }
}
