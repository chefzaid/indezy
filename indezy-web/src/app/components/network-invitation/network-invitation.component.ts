import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { PublicNetworkInvitation } from '../../models';
import { NetworkInvitationsService } from '../../services/network/network-invitations.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * The page an invitation to the network opens, without an account (`/network-invitation#<token>`): who invites, a link
 * to create an account, and a button never to be invited again, so a mailbox's link scanner declines nothing. The token
 * comes after the #, so it never reaches a server log.
 */
@Component({
  selector: 'app-network-invitation',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, TranslateModule, LogoComponent],
  templateUrl: './network-invitation.component.html',
  styleUrls: ['../emails-unsubscribe/emails-unsubscribe.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NetworkInvitationComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly invitations = inject(NetworkInvitationsService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  view?: PublicNetworkInvitation;
  loading = true;
  invalidLink = false;
  sending = false;
  failed = false;
  private token = '';

  ngOnInit(): void {
    this.token = (this.route.snapshot.fragment ?? '').trim();
    if (!this.token) {
      this.loading = false;
      this.invalidLink = true;
      return;
    }
    this.invitations.read(this.token).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: view => this.show(view),
      error: () => {
        this.loading = false;
        this.invalidLink = true;
        this.changes.markForCheck();
      }
    });
  }

  /** Never to be invited to the network again. */
  decline(): void {
    this.sending = true;
    this.failed = false;
    this.invitations.decline(this.token).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: view => {
        this.sending = false;
        this.show(view);
      },
      error: () => {
        this.sending = false;
        this.failed = true;
        this.changes.markForCheck();
      }
    });
  }

  private show(view: PublicNetworkInvitation): void {
    this.view = view;
    this.loading = false;
    this.changes.markForCheck();
  }
}
