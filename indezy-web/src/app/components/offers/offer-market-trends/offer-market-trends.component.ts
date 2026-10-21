import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, Input, LOCALE_ID, OnChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { OfferTrend } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';

/** A row of the trends table: a figure now and its change over each span. */
interface TrendRow {
  label: string;
  now: string;
  changes: { text: string; tone: 'up' | 'down' | 'flat' | 'none' }[];
}

/**
 * How the market of one of the profile's skills moved over 30, 90 and 365 days, in the region the feed is narrowed to
 * or in all of France (see docs/features.md#market-trends).
 */
@Component({
  selector: 'app-offer-market-trends',
  imports: [RouterLink, MatButtonModule, MatFormFieldModule, MatProgressSpinnerModule, MatSelectModule, TranslateModule],
  templateUrl: './offer-market-trends.component.html',
  styleUrls: ['./offer-market-trends.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferMarketTrendsComponent implements OnChanges {
  /** The one region the feed is narrowed to; all of France otherwise. */
  @Input() region?: string;

  trend: OfferTrend | null = null;
  rows: TrendRow[] = [];
  spans: number[] = [];
  loading = true;
  failed = false;

  private readonly offerService = inject(OfferService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly locale = inject(LOCALE_ID);
  private readonly euros = new Intl.NumberFormat(this.locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 });
  private readonly load$ = new Subject<{ skill?: string; region?: string }>();
  private skill?: string;

  constructor() {
    this.load$.pipe(
      tap(() => {
        this.loading = true;
        this.failed = false;
        this.changes.markForCheck();
      }),
      switchMap(({ skill, region }) => this.offerService.marketTrends(skill, region).pipe(catchError(() => of(null)))),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(trend => {
      this.trend = trend;
      this.failed = trend === null;
      this.loading = false;
      this.present(trend);
      this.changes.markForCheck();
    });
  }

  ngOnChanges(): void {
    this.load();
  }

  choose(skill: string): void {
    this.skill = skill;
    this.load();
  }

  load(): void {
    this.load$.next({ skill: this.skill, region: this.region });
  }

  /** Where the market is read: the region, or all of France. */
  get where(): string {
    const region = this.trend?.regionName;
    return region ? this.translate.instant('offers.card.market.inRegion', { region }) : this.translate.instant('offers.card.market.inFrance');
  }

  private present(trend: OfferTrend | null): void {
    const latest = trend?.latest;
    if (!trend || !latest) {
      this.rows = [];
      this.spans = [];
      return;
    }
    const changes = trend.changes ?? [];
    this.spans = changes.map(change => change.days);
    this.rows = [
      { label: this.translate.instant('offers.trends.median'), now: this.euros.format(latest.median),
        changes: changes.map(change => this.change(change.medianPercent)) },
      { label: this.translate.instant('offers.trends.offers'), now: new Intl.NumberFormat(this.locale).format(latest.offers),
        changes: changes.map(change => this.change(change.offersPercent)) }
    ];
  }

  private change(percent: number | undefined): TrendRow['changes'][number] {
    if (percent === undefined || percent === null) {
      return { text: '—', tone: 'none' };
    }
    const text = this.translate.instant('offers.trends.percent', { sign: percent > 0 ? '+' : percent < 0 ? '−' : '', value: Math.abs(percent) });
    return { text, tone: percent > 0 ? 'up' : percent < 0 ? 'down' : 'flat' };
  }
}
