import { ChangeDetectionStrategy, ChangeDetectorRef, Component, LOCALE_ID, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, of, switchMap } from 'rxjs';
import { Offer, OfferPage } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { defaultOfferQuery } from '../../../services/offer/offer-query';
import { offerPlace, offerRate } from '../../../shared/utils/offer-format';

/** Offers the widget shows. */
const SHOWN = 5;

/**
 * The dashboard's new missions: the five offers of the feed not seen yet that match the profile best, or the five
 * newest when the profile names no skills to match. Opening one marks it seen; a failure hides the widget.
 */
@Component({
  selector: 'app-dashboard-offers',
  imports: [MatIconModule, RouterLink, TranslateModule],
  templateUrl: './dashboard-offers.component.html',
  styleUrls: ['./dashboard-offers.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardOffersComponent implements OnInit {
  offers: Offer[] = [];
  /** Whether the offers are the best matches (else the newest). */
  matched = false;
  loaded = false;

  private readonly offerService = inject(OfferService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly locale = inject(LOCALE_ID);

  ngOnInit(): void {
    const unseen = { ...defaultOfferQuery(), unseenOnly: true, size: SHOWN };
    this.offerService.searchQuietly({ ...unseen, sort: 'MATCH' }).pipe(
      switchMap(page => page.matchAvailable ? of(page) : this.offerService.searchQuietly({ ...unseen, sort: 'NEWEST' })),
      catchError(() => of(null))
    ).subscribe((page: OfferPage | null) => {
      this.offers = page?.content ?? [];
      this.matched = !!page?.matchAvailable;
      this.loaded = page !== null;
      this.changes.markForCheck();
    });
  }

  /** The feed with the same offers: unseen ones, by best match or newest. */
  get feedQuery(): Record<string, string> {
    return this.matched ? { sort: 'MATCH', unseenOnly: 'true' } : { unseenOnly: 'true' };
  }

  rate(offer: Offer): string {
    return offerRate(offer, this.locale);
  }

  place(offer: Offer): string {
    return offerPlace(offer);
  }

  /** The offer was opened on its site: it is seen, and leaves the widget. */
  opened(offer: Offer): void {
    this.offers = this.offers.filter(item => item.id !== offer.id);
    this.changes.markForCheck();
    this.offerService.updateState(offer.id, { seen: true }).subscribe({ error: () => undefined });
  }
}
