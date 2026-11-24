import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** The confirmation link's page: confirms the email and opens the account, or says the link no longer works. */
@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, MatCardModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  template: `
    <mat-card class="card">
      <mat-card-header><mat-card-title>{{ 'auth.verification.title' | translate }}</mat-card-title></mat-card-header>
      <mat-card-content>
        @if (failed) {
          <p class="state" role="alert" data-verify-failed><mat-icon aria-hidden="true">link_off</mat-icon>
            <span>{{ 'auth.verification.failed' | translate }}</span></p>
          <p class="back"><a routerLink="/login">{{ 'auth.reset.backToLogin' | translate }}</a></p>
        } @else {
          <p class="state" role="status"><mat-progress-spinner mode="indeterminate" diameter="24"></mat-progress-spinner>
            <span>{{ 'auth.verification.checking' | translate }}</span></p>
        }
      </mat-card-content>
    </mat-card>
  `,
  styleUrls: ['./email-link.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class VerifyEmailComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  failed = false;

  ngOnInit(): void {
    if (!this.token) {
      this.failed = true;
      return;
    }
    this.auth.verifyEmail(this.token).subscribe({
      next: () => {
        this.notifications.success('auth.verification.done');
        this.router.navigateByUrl('/dashboard');
      },
      error: () => {
        this.failed = true;
        this.changes.markForCheck();
      }
    });
  }
}
