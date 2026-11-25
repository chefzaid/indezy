import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** The reset link's page: a new password, typed twice, set and signed in; an expired link says so and offers another. */
@Component({
  selector: 'app-reset-password',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule],
  template: `
    <mat-card class="card">
      <mat-card-header><h1 mat-card-title>{{ 'auth.reset.title' | translate }}</h1></mat-card-header>
      <mat-card-content>
        @if (expired || !token) {
          <p class="state" role="alert" data-reset-expired><mat-icon aria-hidden="true">link_off</mat-icon>
            <span>{{ 'auth.reset.expired' | translate }}</span></p>
          <p class="back"><a routerLink="/forgot-password">{{ 'auth.reset.askAgain' | translate }}</a></p>
        } @else {
          <form [formGroup]="form" (ngSubmit)="save()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>{{ 'auth.reset.password' | translate }}</mat-label>
              <input matInput type="password" formControlName="password" autocomplete="new-password" data-reset-password>
              <mat-hint>{{ 'auth.reset.passwordHint' | translate }}</mat-hint>
            </mat-form-field>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>{{ 'auth.reset.confirm' | translate }}</mat-label>
              <input matInput type="password" formControlName="confirm" autocomplete="new-password" data-reset-confirm>
              @if (form.hasError('mismatch')) {
                <mat-hint class="mismatch">{{ 'auth.reset.mismatch' | translate }}</mat-hint>
              }
            </mat-form-field>
            <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || saving" data-reset-save>{{ 'auth.reset.save' | translate }}</button>
          </form>
        }
      </mat-card-content>
    </mat-card>
  `,
  styleUrls: ['./email-link.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ResetPasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
  readonly form = inject(FormBuilder).nonNullable.group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirm: ['', Validators.required]
  }, { validators: (group: AbstractControl): ValidationErrors | null =>
    group.get('password')?.value === group.get('confirm')?.value ? null : { mismatch: true } });
  saving = false;
  expired = false;

  save(): void {
    if (this.form.invalid || !this.token) {
      return;
    }
    this.saving = true;
    this.auth.resetPassword(this.token, this.form.getRawValue().password).subscribe({
      next: () => {
        this.notifications.success('auth.reset.saved');
        this.router.navigateByUrl('/dashboard');
      },
      error: (error: { status?: number }) => {
        this.saving = false;
        if (error.status === 400) {
          this.expired = true;
        } else {
          this.notifications.error('auth.reset.saveError');
        }
        this.changes.markForCheck();
      }
    });
  }
}
