import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, LOCALE_ID, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { OfferPublisher } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { offerRate } from '../../../shared/utils/offer-format';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/**
 * What a publisher puts in the feed (`/offers/publishers/{name}`, see docs/features.md#publisher-page): its open
 * missions and their rates, how often it publishes a mission again, the sites it posts on, its client record in the CRM,
 * and a switch to keep its offers out of the feed.
 */
@Component({
  selector: 'app-offer-publisher',
  imports: [PageHeaderComponent, DatePipe, RouterModule, MatButtonModule, MatCardModule, MatIconModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './offer-publisher.component.html',
  styleUrls: ['./offer-publisher.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferPublisherComponent implements OnInit {
  publisher?: OfferPublisher;
  loading = true;
  missing = false;
  failed = false;
  busy = false;

  private readonly route = inject(ActivatedRoute);
  private readonly offerService = inject(OfferService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly locale = inject(LOCALE_ID);
  private name = '';

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.name = params.get('name') ?? '';
      this.load();
    });
  }

  load(): void {
    this.loading = true;
    this.missing = false;
    this.failed = false;
    this.offerService.publisher(this.name).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: publisher => {
        this.publisher = publisher;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.publisher = undefined;
        this.missing = error.status === 404;
        this.failed = !this.missing;
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  rate(offer: { rateMin?: number; rateMax?: number }): string {
    return offerRate(offer, this.locale);
  }

  euros(value: number): string {
    return new Intl.NumberFormat(this.locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }).format(value);
  }

  /** Keeps its offers out of the feed, or brings them back. */
  toggleBlock(): void {
    const publisher = this.publisher;
    if (!publisher || this.busy) {
      return;
    }
    this.busy = true;
    const done = (blockId: number | undefined, message: string): void => {
      this.publisher = { ...publisher, blockId };
      this.busy = false;
      this.notifications.success(message, 6000, { publisher: publisher.name });
      this.changes.markForCheck();
    };
    const failed = (): void => {
      this.busy = false;
      this.notifications.error('offers.errors.block');
      this.changes.markForCheck();
    };
    if (publisher.blockId) {
      this.offerService.unblockPublisher(publisher.blockId).subscribe({
        next: () => done(undefined, 'profile.blockedPublishers.unblocked'), error: failed
      });
    } else {
      this.offerService.blockPublisher(publisher.name).subscribe({
        next: block => done(block.id, 'offers.notifications.publisherBlocked'), error: failed
      });
    }
  }
}
