import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Offer, OfferView } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferShareService } from '../../../services/offer/offer-share.service';
import { OfferCardComponent } from '../offer-card/offer-card.component';
import { OfferActions, OfferActionsHost } from '../offer-feed/offer-actions';

/**
 * One offer's own page (`/offers/{id}`, see docs/features.md#offer-page): its full description, facts, copies and match,
 * the same actions as in the feed, and a link to share; it says when the offer expired or left the feed.
 */
@Component({
  selector: 'app-offer-page',
  imports: [DatePipe, RouterModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, TranslateModule,
    OfferCardComponent],
  templateUrl: './offer-page.component.html',
  styleUrls: ['./offer-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferPageComponent implements OnInit, OfferActionsHost {
  offer?: Offer;
  loading = true;
  /** The offer is no longer collected (or never was). */
  missing = false;
  failed = false;
  /** Hiding the offer takes it out of the feed; its page keeps showing it. */
  readonly view: OfferView = 'FEED';
  readonly importing = new Set<number>();

  private readonly route = inject(ActivatedRoute);
  private readonly offerService = inject(OfferService);
  private readonly share = inject(OfferShareService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly actions = new OfferActions(this, this.offerService, this.notifications, inject(MatDialog), inject(TranslateService));
  private id?: number;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = Number(params.get('id'));
      this.id = Number.isInteger(id) && id > 0 ? id : undefined;
      this.load();
    });
  }

  load(): void {
    this.offer = undefined;
    this.missing = !this.id;
    this.failed = false;
    this.loading = !!this.id;
    if (!this.id) {
      return;
    }
    this.offerService.get(this.id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: offer => {
        this.offer = offer;
        this.loading = false;
        this.actions.markSeen(offer);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.missing = error.status === 404;
        this.failed = !this.missing;
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  copyLink(): void {
    if (this.offer) {
      this.share.copyLink(this.offer.id);
    }
  }

  patch(id: number, changes: Partial<Offer>): void {
    if (this.offer?.id === id) {
      this.offer = { ...this.offer, ...changes };
    }
    this.changes.markForCheck();
  }

  /** The page keeps its offer: the feed is where it leaves a view. */
  drop(): void {
    this.changes.markForCheck();
  }

  checkpoint(): () => void {
    const before = this.offer;
    return () => {
      this.offer = before;
      this.changes.markForCheck();
    };
  }
}
