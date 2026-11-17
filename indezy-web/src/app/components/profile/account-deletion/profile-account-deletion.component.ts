import { ChangeDetectionStrategy, ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';

import { AccountDeletionService } from '../../../services/account/account-deletion.service';
import { AuthService } from '../../../services/auth/auth.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** How long an account stays on hold before it is deleted for good. */
export const HOLD_DAYS = 30;

/**
 * Deletes the reader's account, once they type its email: it goes on hold at once (signed out everywhere, hidden from the
 * network) and is deleted for good after 30 days, unless they sign in before then and restore it.
 */
@Component({
  selector: 'app-profile-account-deletion',
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule, DatePipe],
  templateUrl: './profile-account-deletion.component.html',
  styleUrl: './profile-account-deletion.component.scss',
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileAccountDeletionComponent {
  private readonly api = inject(AccountDeletionService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);

  readonly confirmation = new FormControl('', { nonNullable: true });
  readonly email = this.auth.getUser()?.email ?? '';
  /** The day the account would be deleted, were it put on hold today. */
  readonly purgeOn = new Date(Date.now() + HOLD_DAYS * 24 * 60 * 60 * 1000);
  asking = false;
  sending = false;

  /** Whether the email typed is the account's (case and spaces aside). */
  get confirmed(): boolean {
    return this.email !== '' && this.confirmation.value.trim().toLowerCase() === this.email.toLowerCase();
  }

  ask(open: boolean): void {
    this.asking = open;
    this.confirmation.reset('');
  }

  delete(): void {
    if (!this.confirmed || this.sending) {
      return;
    }
    this.sending = true;
    this.api.request(this.confirmation.value.trim()).subscribe({
      next: hold => {
        this.auth.forgetSession();
        this.router.navigate(['/login'], { queryParams: { onHold: hold.purgeOn } });
      },
      error: () => {
        this.sending = false;
        this.notifications.error('profile.deletion.error');
        this.changes.markForCheck();
      }
    });
  }
}
