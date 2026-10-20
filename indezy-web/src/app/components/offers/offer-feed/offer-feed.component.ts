import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, LOCALE_ID, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { BehaviorSubject, catchError, combineLatest, forkJoin, map, of, switchMap, tap } from 'rxjs';
import {
  Offer,
  OfferFilters,
  OfferPage,
  OfferQuery,
  OfferRegion,
  OfferSiteInfo,
  OfferSort,
  OfferStateUpdate,
  OfferView
} from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import {
  OFFER_PAGE_SIZES,
  countActiveFilters,
  defaultOfferQuery,
  offerQueryFromParams,
  offerQueryToParams
} from '../../../services/offer/offer-query';
import { NotificationService } from '../../../services/notification/notification.service';
import { relativeTime } from '../../../shared/utils/relative-time';
import { OfferCardComponent } from '../offer-card/offer-card.component';
import { OfferFiltersComponent } from '../offer-filters/offer-filters.component';
import { OfferSavedSearchesComponent } from '../offer-saved-searches/offer-saved-searches.component';

/** The freelance missions of every job board in one feed, newest first, with filters kept in the address. */
@Component({
  selector: 'app-offer-feed',
  imports: [MatButtonModule, MatButtonToggleModule, MatCardModule, MatFormFieldModule, MatIconModule, MatPaginatorModule,
    MatProgressSpinnerModule, MatSelectModule, TranslateModule, OfferCardComponent, OfferFiltersComponent, OfferSavedSearchesComponent],
  templateUrl: './offer-feed.component.html',
  styleUrls: ['./offer-feed.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferFeedComponent implements OnInit {
  readonly views: OfferView[] = ['FEED', 'SAVED', 'HIDDEN'];
  readonly sorts: OfferSort[] = ['NEWEST', 'RATE', 'DISTANCE'];
  readonly pageSizes = OFFER_PAGE_SIZES;

  query: OfferQuery = defaultOfferQuery();
  page: OfferPage | null = null;
  regions: OfferRegion[] = [];
  sites: OfferSiteInfo[] = [];
  loading = true;
  failed = false;
  /** Filters panel open on small screens. */
  filtersOpen = false;
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
  /** Searches again with the same filters (after a failure). */
  private readonly reload$ = new BehaviorSubject<void>(undefined);

  ngOnInit(): void {
    forkJoin({
      regions: this.offerService.regions().pipe(catchError(() => of([]))),
      sites: this.offerService.sites().pipe(catchError(() => of([])))
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(({ regions, sites }) => {
      this.regions = regions;
      this.sites = sites;
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
      switchMap(query => this.offerService.search(query).pipe(catchError(() => of(null)))),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(page => {
      // A new page brings its offers without descriptions: fold every card.
      this.expanded.clear();
      this.page = page;
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

  get openOffers(): number {
    return this.sites.reduce((total, site) => total + site.openOffers, 0);
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

  retry(): void {
    this.reload$.next();
  }

  toggleSaved(offer: Offer): void {
    const saved = !offer.saved;
    this.change(offer, { saved }, this.query.view === 'SAVED' && !saved);
  }

  toggleHidden(offer: Offer): void {
    const hidden = !offer.hidden;
    const leavesView = this.query.view === 'FEED' ? hidden : this.query.view === 'HIDDEN' && !hidden;
    this.change(offer, { hidden }, leavesView);
  }

  opened(offer: Offer): void {
    this.markSeen(offer);
  }

  toggleDetails(offer: Offer): void {
    if (this.expanded.has(offer.id)) {
      this.expanded.delete(offer.id);
      this.patch(offer.id, {});
      return;
    }
    this.markSeen(offer);
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

  importOffer(offer: Offer): void {
    this.importing.add(offer.id);
    this.offerService.importOffer(offer.id).subscribe({
      next: project => {
        this.importing.delete(offer.id);
        this.patch(offer.id, { importedProjectId: project.id, seen: true });
        this.notifications.success('offers.notifications.imported', 4000, { role: offer.title });
      },
      error: (error: HttpErrorResponse) => {
        this.importing.delete(offer.id);
        this.patch(offer.id, {});
        if (error.status === 409) {
          this.notifications.error('offers.errors.alreadyImported');
          this.offerService.get(offer.id).subscribe(full => this.patch(offer.id, { importedProjectId: full.importedProjectId }));
        } else {
          this.notifications.error('offers.errors.import');
        }
      }
    });
  }

  private markSeen(offer: Offer): void {
    if (!offer.seen) {
      this.patch(offer.id, { seen: true });
      this.offerService.updateState(offer.id, { seen: true }).subscribe({ error: () => undefined });
    }
  }

  /** Applies a state change at once, takes the card out of a view it no longer belongs to, and undoes both on failure. */
  private change(offer: Offer, update: OfferStateUpdate, leavesView: boolean): void {
    const before = this.page;
    this.patch(offer.id, update);
    if (leavesView && this.page) {
      this.page = { ...this.page, content: this.page.content.filter(item => item.id !== offer.id),
        totalElements: this.page.totalElements - 1 };
    }
    this.offerService.updateState(offer.id, update).subscribe({
      error: () => {
        this.page = before;
        this.notifications.error('offers.errors.state');
        this.changes.markForCheck();
      }
    });
  }

  /** Replaces one offer of the page with a changed copy, so its card redraws. */
  private patch(id: number, changes: Partial<Offer>): void {
    if (this.page) {
      this.page = { ...this.page, content: this.page.content.map(item => item.id === id ? { ...item, ...changes } : item) };
    }
    this.changes.markForCheck();
  }

  private navigate(query: OfferQuery): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: offerQueryToParams(query) });
  }
}
