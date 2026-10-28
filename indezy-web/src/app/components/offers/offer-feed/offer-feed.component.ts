import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, LOCALE_ID, OnInit, QueryList, ViewChildren, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, catchError, combineLatest, forkJoin, map, of, switchMap, tap } from 'rxjs';
import {
  Offer,
  OfferFilters,
  OfferPage,
  OfferQuery,
  OfferRegion,
  OfferSiteInfo,
  OfferSort,
  OfferView
} from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import {
  OFFER_PAGE_SIZES,
  countActiveFilters,
  defaultOfferQuery,
  offerQueryFromParams,
  offerQueryToParams,
  sameOfferFilters
} from '../../../services/offer/offer-query';
import { NotificationService } from '../../../services/notification/notification.service';
import { dropOffers, patchOffers } from '../../../services/offer/offer-page';
import { relativeTime } from '../../../shared/utils/relative-time';
import { OfferCardComponent } from '../offer-card/offer-card.component';
import { OfferSelectionBarComponent } from '../offer-selection-bar/offer-selection-bar.component';
import { OfferBulkActions, OfferPageHost } from './offer-bulk-actions';
import { OfferActions, OfferActionsHost } from './offer-actions';
import { OfferFiltersComponent } from '../offer-filters/offer-filters.component';
import { OfferMarketComponent } from '../offer-market/offer-market.component';
import { OfferKeyAction, OfferListKeysDirective } from './offer-list-keys.directive';
import { OfferSavedSearchesComponent } from '../offer-saved-searches/offer-saved-searches.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/** Whether the market snapshot was left open, in this browser. */
const MARKET_OPEN_KEY = 'indezy.offers.marketOpen';

/** The freelance missions of every job board in one feed, newest first, with filters kept in the address. */
@Component({
  selector: 'app-offer-feed',
  imports: [PageHeaderComponent, MatButtonModule, MatButtonToggleModule, MatCardModule, MatFormFieldModule, MatIconModule, MatPaginatorModule,
    MatProgressSpinnerModule, MatSelectModule, RouterLink, TranslateModule, OfferCardComponent, OfferSelectionBarComponent, OfferFiltersComponent, OfferMarketComponent,
    OfferSavedSearchesComponent, OfferListKeysDirective],
  templateUrl: './offer-feed.component.html',
  styleUrls: ['./offer-feed.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferFeedComponent implements OnInit, OfferPageHost, OfferActionsHost {
  readonly views: OfferView[] = ['FEED', 'SAVED', 'APPLIED', 'HIDDEN'];
  readonly sorts: OfferSort[] = ['NEWEST', 'RATE', 'DISTANCE', 'MATCH'];
  readonly pageSizes = OFFER_PAGE_SIZES;

  query: OfferQuery = defaultOfferQuery();
  page: OfferPage | null = null;
  regions: OfferRegion[] = [];
  sites: OfferSiteInfo[] = [];
  /** The sites the workspace keeps in its feed. */
  keptSites: OfferSiteInfo[] = [];
  loading = true;
  failed = false;
  /** Filters panel open on small screens. */
  filtersOpen = false;
  marketOpen = readMarketOpen();
  /** The cards of the page, in its order. */
  @ViewChildren(OfferCardComponent) private cards?: QueryList<OfferCardComponent>;
  readonly expanded = new Set<number>();
  readonly loadingDetails = new Set<number>();
  readonly importing = new Set<number>();

  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly offerService = inject(OfferService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly locale = inject(LOCALE_ID);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  /** The offers picked for an action on several at once. */
  readonly bulk = new OfferBulkActions(this, this.offerService, this.notifications);
  readonly actions = new OfferActions(this, this.offerService, this.notifications, this.dialog, this.translate);
  /** Searches again with the same filters (after a failure). */
  private readonly reload$ = new BehaviorSubject<void>(undefined);
  /** The query of the page shown, whose total another page or sort of the same filters reuses. */
  private shownQuery: OfferQuery | null = null;

  ngOnInit(): void {
    forkJoin({
      regions: this.offerService.regions().pipe(catchError(() => of([]))),
      sites: this.offerService.sites().pipe(catchError(() => of([])))
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ regions, sites }) => {
      this.regions = regions;
      this.sites = sites;
      this.keptSites = sites.filter(site => !site.excluded);
      this.changes.markForCheck();
    });
    combineLatest([this.route.queryParamMap, this.reload$]).pipe(
      map(([params]) => offerQueryFromParams(params)),
      tap(query => {
        this.query = query;
        this.loading = true;
        this.failed = false;
        this.changes.markForCheck();
      }),
      switchMap(query => this.offerService.search(query, this.knownTotal(query)).pipe(
        catchError(() => of(null)),
        map(page => ({ query, page }))
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(({ query, page }) => {
      // A new page brings its offers without descriptions: fold every card.
      this.expanded.clear();
      this.bulk.clear();
      this.page = page;
      this.shownQuery = page ? query : null;
      this.failed = page === null;
      this.loading = false;
      this.changes.markForCheck();
    });
  }

  get filters(): OfferFilters {
    return this.query;
  }

  get activeFilters(): number {
    return countActiveFilters(this.query);
  }

  /** Whether a sort needs what the profile does not say yet. */
  sortUnavailable(sort: OfferSort): boolean {
    return (sort === 'DISTANCE' && !this.page?.distanceAvailable) || (sort === 'MATCH' && !this.page?.matchAvailable);
  }

  get openOffers(): number {
    return this.keptSites.reduce((total, site) => total + site.openOffers, 0);
  }

  get failingSites(): number {
    return this.keptSites.filter(site => site.failing).length;
  }

  /** When the most recently refreshed site was last read, in words. */
  get lastUpdate(): string | null {
    const times = this.sites.map(site => site.lastSuccessAt).filter((time): time is string => !!time).sort();
    return times.length ? relativeTime(times[times.length - 1], this.locale) : null;
  }

  onFiltersChange(filters: OfferFilters): void {
    this.navigate({ ...this.query, ...filters, page: 0 });
  }

  setView(view: OfferView): void {
    this.navigate({ ...this.query, view, page: 0 });
  }

  setSort(sort: OfferSort): void {
    this.navigate({ ...this.query, sort, page: 0 });
  }

  onPage(event: PageEvent): void {
    this.navigate({ ...this.query, page: event.pageIndex, size: event.pageSize });
  }

  openSavedSearch(query: OfferQuery): void {
    this.navigate(query);
  }

  clearFilters(): void {
    this.navigate({ ...defaultOfferQuery(), view: this.query.view, sort: this.query.sort });
  }

  redraw(): void {
    this.changes.markForCheck();
  }

  retry(): void {
    this.reload$.next();
  }

  toggleMarket(): void {
    this.marketOpen = !this.marketOpen;
    try {
      localStorage.setItem(MARKET_OPEN_KEY, String(this.marketOpen));
    } catch {
      // Storage blocked: the panel just opens closed next time.
    }
  }

  /** A key pressed on an offer of the list (see {@link OfferListKeysDirective}). */
  onOfferKey(event: { action: OfferKeyAction; index: number }): void {
    const offer = this.page?.content[event.index];
    if (!offer) {
      return;
    }
    switch (event.action) {
      case 'save':
        this.actions.toggleSaved(offer);
        break;
      case 'hide':
        this.actions.toggleHidden(offer);
        break;
      case 'add':
        if (!offer.importedProjectId && !this.importing.has(offer.id)) {
          this.actions.importOffer(offer);
        }
        break;
      case 'details':
        this.toggleDetails(offer);
        break;
      case 'note':
        this.cards?.get(event.index)?.editNote();
        break;
      case 'select':
        this.bulk.toggle(offer);
        break;
    }
  }

  /** Narrows the feed to a skill the market snapshot names. */
  filterOnSkill(skill: string): void {
    this.navigate({ ...this.query, skills: [...this.query.skills, skill], page: 0 });
  }

  toggleDetails(offer: Offer): void {
    if (this.expanded.has(offer.id)) {
      this.expanded.delete(offer.id);
      this.patch(offer.id, {});
      return;
    }
    this.actions.markSeen(offer);
    if (offer.description) {
      this.expanded.add(offer.id);
      this.patch(offer.id, {});
      return;
    }
    this.loadingDetails.add(offer.id);
    this.offerService.get(offer.id).subscribe({
      next: full => {
        this.loadingDetails.delete(offer.id);
        this.expanded.add(offer.id);
        this.patch(offer.id, { description: full.description ?? offer.excerpt });
      },
      error: () => {
        this.loadingDetails.delete(offer.id);
        this.patch(offer.id, {});
        this.notifications.error('offers.errors.details');
      }
    });
  }

  /** The view of the page: an offer that leaves it is taken out (see {@link OfferActionsHost}). */
  get view(): OfferView {
    return this.query.view;
  }

  drop(matching: (offer: Offer) => boolean): void {
    if (this.page) {
      this.page = dropOffers(this.page, matching);
    }
    this.changes.markForCheck();
  }

  checkpoint(): () => void {
    const before = this.page;
    return () => {
      this.page = before;
      this.changes.markForCheck();
    };
  }

  /** Replaces one offer of the page with a changed copy, so its card redraws. */
  patch(id: number, changes: Partial<Offer>): void {
    if (this.page) {
      this.page = patchOffers(this.page, [id], changes);
    }
    this.changes.markForCheck();
  }

  /** The total of the page shown, offers taken out of it included, when {@code query} asks another page or sort of its filters. */
  private knownTotal(query: OfferQuery): number | undefined {
    return this.page && this.shownQuery && sameOfferFilters(query, this.shownQuery) ? this.page.totalElements : undefined;
  }

  private navigate(query: OfferQuery): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: offerQueryToParams(query) });
  }
}

function readMarketOpen(): boolean {
  try {
    return localStorage.getItem(MARKET_OPEN_KEY) === 'true';
  } catch {
    return false;
  }
}
