import {
  ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, LOCALE_ID, OnChanges, Output, inject
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';
import { OfferFacet, OfferMarket, OfferQuery } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferBarChartComponent } from '../offer-bar-chart/offer-bar-chart.component';
import { OfferMarketTrendsComponent } from '../offer-market-trends/offer-market-trends.component';

type RemotePart = 'remote' | 'hybrid' | 'onsite' | 'unstated';

/** What the template shows of a snapshot, computed once per snapshot. */
interface MarketView {
  market: OfferMarket;
  statedShare: number;
  bins: { labels: string[]; values: number[]; target: number | null; description: string };
  remote: { part: RemotePart; offers: number; share: number }[];
  days: { labels: string[]; values: number[]; description: string };
}

const REMOTE_PARTS: RemotePart[] = ['remote', 'hybrid', 'onsite', 'unstated'];

/** `Intl.NumberFormat` with its ES2023 `formatRange`, which every supported browser has. */
type RangeFormat = Intl.NumberFormat & { formatRange(start: number, end: number): string };

function absent(value: number | null | undefined): value is null | undefined {
  return value === null || value === undefined;
}

/**
 * The market the current filters describe: daily rates and where the target stands, the remote split, the most named
 * skills (a click filters on one) and intermediaries, and offers published per day. Fetched again when the filters or
 * the view change, not for another page or sort.
 */
@Component({
  selector: 'app-offer-market',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, RouterLink, TranslateModule, OfferBarChartComponent,
    OfferMarketTrendsComponent],
  templateUrl: './offer-market.component.html',
  styleUrls: ['./offer-market.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferMarketComponent implements OnChanges {
  @Input({ required: true }) query!: OfferQuery;
  @Output() readonly skillChosen = new EventEmitter<string>();

  readonly remoteParts = REMOTE_PARTS;
  view: MarketView | null = null;
  loading = true;
  failed = false;

  private readonly offerService = inject(OfferService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly locale = inject(LOCALE_ID);
  private readonly number = new Intl.NumberFormat(this.locale, { maximumFractionDigits: 1 });
  private readonly euros = new Intl.NumberFormat(this.locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 }) as RangeFormat;
  private readonly dayFormat = new Intl.DateTimeFormat(this.locale, { day: 'numeric', month: 'short' });
  private readonly query$ = new Subject<OfferQuery>();
  private filtersKey?: string;

  constructor() {
    this.query$.pipe(
      tap(() => {
        this.loading = true;
        this.failed = false;
        this.changes.markForCheck();
      }),
      switchMap(query => this.offerService.market(query).pipe(catchError(() => of(null)))),
      takeUntilDestroyed()
    ).subscribe(market => {
      this.view = market ? this.present(market) : null;
      this.failed = market === null;
      this.loading = false;
      this.changes.markForCheck();
    });
  }

  /** The one region the feed is narrowed to, where the trends are read. */
  get trendRegion(): string | undefined {
    return this.query.regions.length === 1 ? this.query.regions[0] : undefined;
  }

  ngOnChanges(): void {
    const key = JSON.stringify({ ...this.query, page: 0, size: 0, sort: 'NEWEST' });
    if (key !== this.filtersKey) {
      this.filtersKey = key;
      this.query$.next(this.query);
    }
  }

  retry(): void {
    this.query$.next(this.query);
  }

  formatEuros(amount: number | null | undefined): string {
    return absent(amount) ? '–' : this.euros.format(amount);
  }

  /** Two amounts as the locale writes a range: "€520 – €680", "520–680 €". */
  formatEuroRange(from: number | null | undefined, to: number | null | undefined): string {
    return absent(from) || absent(to) ? '–' : this.euros.formatRange(from, to);
  }

  formatNumber(value: number): string {
    return this.number.format(value);
  }

  /** A bar's length against the first (largest) of its ranking. */
  barWidth(facet: OfferFacet, ranking: OfferFacet[]): number {
    return ranking.length ? Math.round(100 * facet.offers / ranking[0].offers) : 0;
  }

  isFiltered(skill: string): boolean {
    return this.query.skills.some(chosen => chosen.toLowerCase() === skill.toLowerCase());
  }

  private present(market: OfferMarket): MarketView {
    const offersWord = this.translate.instant('offers.market.offersUnit');
    const binLabels = market.rateBins.map(bin => this.binLabel(bin.from, bin.to));
    const target = market.rates.target;
    const targetBin = absent(target) ? -1 : market.rateBins.findIndex(bin => (absent(bin.from) || target >= bin.from) &&
      (absent(bin.to) || target < bin.to));
    const dayLabels = market.perDay.map(day => this.dayFormat.format(this.localDate(day.day)));
    return {
      market,
      statedShare: market.offers ? Math.round(100 * market.rates.stated / market.offers) : 0,
      bins: {
        labels: binLabels,
        values: market.rateBins.map(bin => bin.offers),
        target: targetBin < 0 ? null : targetBin,
        description: this.describe('offers.market.rates', binLabels, market.rateBins.map(bin => bin.offers), offersWord)
      },
      remote: REMOTE_PARTS.map(part => ({
        part,
        offers: market.remote[part],
        share: market.offers ? Math.round(100 * market.remote[part] / market.offers) : 0
      })).filter(part => part.offers > 0),
      days: {
        labels: dayLabels,
        values: market.perDay.map(day => day.offers),
        description: this.describe('offers.market.perDay', dayLabels, market.perDay.map(day => day.offers), offersWord)
      }
    };
  }

  private binLabel(from: number | null | undefined, to: number | null | undefined): string {
    if (absent(from)) {
      return `< ${this.formatEuros(to)}`;
    }
    if (absent(to)) {
      return `≥ ${this.formatEuros(from)}`;
    }
    return this.formatEuroRange(from, to);
  }

  private describe(titleKey: string, labels: string[], values: number[], unit: string): string {
    const title = this.translate.instant(titleKey);
    return [title, ...labels.map((label, index) => `${label}: ${values[index]} ${unit}`)].join('; ');
  }

  /** A `YYYY-MM-DD` day at local midnight, so formatting does not shift it. */
  private localDate(day: string): Date {
    const [year, month, date] = day.split('-').map(Number);
    return new Date(year, month - 1, date);
  }
}
