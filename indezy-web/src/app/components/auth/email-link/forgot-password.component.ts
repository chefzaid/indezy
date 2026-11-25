import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** **Forgot your password?**: the email to send a reset link to; the page says the same whether an account exists or not. */
@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule],
  template: `
    <mat-card class="card">
      <mat-card-header><h1 mat-card-title>{{ 'auth.reset.forgotTitle' | translate }}</h1></mat-card-header>
      <mat-card-content>
        @if (sent) {
          <p class="state" role="status" data-forgot-sent><mat-icon aria-hidden="true">mark_email_read</mat-icon>
            <span>{{ 'auth.reset.sent' | translate }}</span></p>
        } @else {
          <p>{{ 'auth.reset.forgotIntro' | translate }}</p>
          <form [formGroup]="form" (ngSubmit)="send()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>{{ 'common.email' | translate }}</mat-label>
              <input matInput type="email" formControlName="email" autocomplete="email" data-forgot-email>
            </mat-form-field>
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || sending" data-forgot-send>{{ 'auth.reset.send' | translate }}</button>
          </form>
        }
        <p class="back"><a routerLink="/login">{{ 'auth.reset.backToLogin' | translate }}</a></p>
      </mat-card-content>
    </mat-card>
  `,
  styleUrls: ['./email-link.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ForgotPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly form = inject(FormBuilder).nonNullable.group({ email: ['', [Validators.required, Validators.email]] });
  sending = false;
  sent = false;

  send(): void {
    if (this.form.invalid) {
      return;
    }
    this.sending = true;
    this.auth.forgotPassword(this.form.getRawValue().email.trim()).subscribe({
      next: () => {
        this.sent = true;
        this.sending = false;
        this.changes.markForCheck();
      },
      error: () => {
        this.sending = false;
        this.notifications.error('auth.reset.sendError');
        this.changes.markForCheck();
      }
    });
  }
}
