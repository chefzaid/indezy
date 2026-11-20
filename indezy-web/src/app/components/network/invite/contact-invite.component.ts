import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, switchMap } from 'rxjs';

import { ContactStanding } from '../../../models';
import { CapabilitiesService } from '../../../services/admin/capabilities.service';
import { NetworkInvitationsService } from '../../../services/network/network-invitations.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

/**
 * Where a contact stands toward the member network, on their page: **Invite to the network** (an email, once, once
 * confirmed), invited on a date, their page once a member, or nothing more for one who declined every invitation. Shown
 * to the network's members only, while the network is on.
 */
@Component({
  selector: 'app-contact-invite',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    @if (standing; as current) {
      <div class="invite" data-contact-invite>
        @switch (current.standing) {
          @case ('INVITABLE') {
            <button mat-stroked-button type="button" (click)="invite()" [disabled]="sending" data-contact-invite-button>
              <mat-icon>group_add</mat-icon>{{ 'network.invite.button' | translate }}
            </button>
          }
          @case ('INVITED') {
            <span class="state" data-contact-invited><mat-icon aria-hidden="true">mark_email_read</mat-icon>
              {{ 'network.invite.invited' | translate: { date: (current.invitedAt | date:'mediumDate') } }}</span>
          }
          @case ('MEMBER') {
            <a mat-stroked-button [routerLink]="['/m', current.handle]" data-contact-member>
              <mat-icon>badge</mat-icon>{{ 'network.invite.member' | translate }}
            </a>
          }
          @case ('DECLINED') {
            <span class="state" data-contact-declined><mat-icon aria-hidden="true">do_not_disturb_on</mat-icon>{{ 'network.invite.declined' | translate }}</span>
          }
        }
      </div>
    }
  `,
  styles: [`
    .invite { display: inline-flex; align-items: center; }
    .state { display: inline-flex; align-items: center; gap: 6px; color: var(--app-text-muted); font-size: 0.875rem; }
    .state mat-icon { width: 18px; height: 18px; font-size: 18px; }
    button, a { border-radius: 999px; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ContactInviteComponent implements OnChanges {
  private readonly invitations = inject(NetworkInvitationsService);
  private readonly capabilities = inject(CapabilitiesService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) contactId!: number;
  /** The contact's name, for the confirmation. */
  @Input() name = '';
  standing: ContactStanding | null = null;
  sending = false;

  ngOnChanges(): void {
    this.standing = null;
    if (this.capabilities.snapshot().features.SOCIAL_NETWORK === false) {
      return;
    }
    // A visitor of the network (409) or a contact out of reach shows nothing.
    this.invitations.standing(this.contactId).subscribe({
      next: standing => {
        this.standing = standing.standing === 'NO_EMAIL' ? null : standing;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** Emails the invitation once confirmed. */
  invite(): void {
    this.confirm.confirm({ messageKey: 'network.invite.confirm', messageParams: { name: this.name }, confirmKey: 'network.invite.send' })
      .pipe(switchMap(confirmed => {
        if (!confirmed) {
          return EMPTY;
        }
        this.sending = true;
        this.changes.markForCheck();
        return this.invitations.invite(this.contactId);
      }))
      .subscribe({
        next: standing => {
          this.sending = false;
          this.standing = standing;
          this.notifications.success('network.invite.sent', undefined, { name: this.name });
          this.changes.markForCheck();
        },
        error: (error: HttpErrorResponse) => {
          this.sending = false;
          this.notifications.error(error.status === 429 ? 'network.invite.tooMany' : 'network.invite.error');
          this.changes.markForCheck();
        }
      });
  }
}
