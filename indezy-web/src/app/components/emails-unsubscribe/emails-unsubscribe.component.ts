import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { PublicSavedSearchEmails } from '../../models/offer.models';
import { OfferService } from '../../services/offer/offer.service';
import { LogoComponent } from '../../shared/components/logo/logo.component';

/**
 * The page a saved search email's unsubscribe link opens, without an account: the search it stops, and a button that
 * stops it, so a mailbox's link scanner opening the page stops nothing. The token comes after the # of the link, so it
 * never reaches a server log.
 */
@Component({
  selector: 'app-emails-unsubscribe',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, TranslateModule, LogoComponent],
  templateUrl: './emails-unsubscribe.component.html',
  styleUrls: ['./emails-unsubscribe.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EmailsUnsubscribeComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly offers = inject(OfferService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  view?: PublicSavedSearchEmails;
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
    this.offers.publicSearchEmails(this.token).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: view => this.show(view),
      error: () => {
        this.loading = false;
        this.invalidLink = true;
        this.changes.markForCheck();
      }
    });
  }

  unsubscribe(): void {
    this.sending = true;
    this.failed = false;
    this.offers.unsubscribeSearchEmails(this.token).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
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

  private show(view: PublicSavedSearchEmails): void {
    this.view = view;
    this.loading = false;
    this.changes.markForCheck();
  }
}
