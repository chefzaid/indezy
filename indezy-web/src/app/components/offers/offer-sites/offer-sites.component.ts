import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, LOCALE_ID, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';
import { interval } from 'rxjs';
import { OfferLeftOutSite, OfferSiteHealth, OfferSiteHealthEntry } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { relativeTime } from '../../../shared/utils/relative-time';
import { OfferSitesToVisitComponent } from '../offer-sites-to-visit/offer-sites-to-visit.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/** A site whose newest offer is older than this reads as quiet. */
const QUIET_AFTER_MS = 30 * 86_400_000;
/** How often the page looks again at which sites can be read on request. */
const CLOCK_TICK_MS = 30_000;

/**
 * The job boards of the feed: how each one's collection goes, a switch to keep one out of the workspace's feed, and
 * reading one now (at most once every 15 minutes, whoever asks), with the boards left out and why.
 */
@Component({
  selector: 'app-offer-sites',
  imports: [PageHeaderComponent, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatSlideToggleModule, RouterLink, TranslateModule,
    OfferSitesToVisitComponent],
  templateUrl: './offer-sites.component.html',
  styleUrls: ['./offer-sites.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferSitesComponent implements OnInit {
  health: OfferSiteHealth | null = null;
  /** The sites left out, neither to visit nor matching services. */
  leftOutOnly: OfferLeftOutSite[] = [];
  loading = true;
  failed = false;
  readonly saving = new Set<string>();
  readonly refreshing = new Set<string>();

  private readonly route = inject(ActivatedRoute);
  private readonly offerService = inject(OfferService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly locale = inject(LOCALE_ID);
  private readonly number = new Intl.NumberFormat(this.locale);
  private now = Date.now();

  ngOnInit(): void {
    this.load();
    interval(CLOCK_TICK_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.now = Date.now();
      this.changes.markForCheck();
    });
  }

  load(): void {
    this.loading = true;
    this.failed = false;
    this.changes.markForCheck();
    this.offerService.siteHealth().subscribe({
      next: health => {
        this.health = health;
        this.leftOutOnly = health.leftOut.filter(site => site.kind === 'OTHER');
        this.now = Date.now();
        this.loading = false;
        this.changes.markForCheck();
        this.scrollToFragment();
      },
      error: () => {
        this.failed = true;
        this.loading = false;
        this.changes.markForCheck();
      }
    });
  }

  /** The feed links to a section of the page (`#sites-to-visit`): show it once the page is drawn. */
  private scrollToFragment(): void {
    const fragment = this.route.snapshot.fragment;
    if (fragment) {
      setTimeout(() => document.getElementById(fragment)?.scrollIntoView({ block: 'start' }));
    }
  }

  get failingCount(): number {
    return this.health?.sites.filter(site => site.status === 'FAILING').length ?? 0;
  }

  when(moment: string | null | undefined): string | null {
    return moment ? relativeTime(moment, this.locale, new Date(this.now)) : null;
  }

  formatNumber(value: number): string {
    return this.number.format(value);
  }

  isDue(site: OfferSiteHealthEntry): boolean {
    return !site.nextRunAt || new Date(site.nextRunAt).getTime() <= this.now;
  }

  /** No offer published for a month: the board may be dying. */
  isQuiet(site: OfferSiteHealthEntry): boolean {
    return !!site.newestOfferAt && this.now - new Date(site.newestOfferAt).getTime() > QUIET_AFTER_MS;
  }

  /** When the site can be read on request again, in the future only. */
  refreshAt(site: OfferSiteHealthEntry): string | null {
    return site.refreshAvailableAt && new Date(site.refreshAvailableAt).getTime() > this.now ? site.refreshAvailableAt : null;
  }

  canRefresh(site: OfferSiteHealthEntry): boolean {
    return !!this.health?.refreshEnabled && site.status !== 'RUNNING' && !this.refreshAt(site) && !this.refreshing.has(site.site);
  }

  setIncluded(site: OfferSiteHealthEntry, included: boolean): void {
    const excluded = !included;
    this.saving.add(site.site);
    this.patch(site.site, { excluded });
    this.offerService.setSiteExcluded(site.site, excluded).subscribe({
      next: () => {
        this.saving.delete(site.site);
        this.notifications.success(excluded ? 'offers.sites.notifications.excluded' : 'offers.sites.notifications.included', 3000,
          { site: site.name });
        this.changes.markForCheck();
      },
      error: () => {
        this.saving.delete(site.site);
        this.patch(site.site, { excluded: !excluded });
        this.notifications.error('offers.sites.errors.exclusion');
      }
    });
  }

  refresh(site: OfferSiteHealthEntry): void {
    this.refreshing.add(site.site);
    this.changes.markForCheck();
    this.offerService.refreshSite(site.site).subscribe({
      next: answer => {
        this.refreshing.delete(site.site);
        this.patch(site.site, { refreshAvailableAt: answer.refreshAvailableAt });
        this.notifications.success('offers.sites.notifications.refreshStarted', 6000, { site: site.name });
      },
      error: (error: HttpErrorResponse) => {
        this.refreshing.delete(site.site);
        if (error.status === 429) {
          const seconds = Number(error.headers?.get('Retry-After')) || 60;
          this.now = Date.now();
          const availableAt = new Date(this.now + seconds * 1000).toISOString();
          this.patch(site.site, { refreshAvailableAt: availableAt });
          this.notifications.error('offers.sites.errors.refreshTooSoon', 6000, { site: site.name, when: this.when(availableAt) });
        } else if (error.status === 409) {
          this.notifications.error('offers.sites.refreshOff');
          this.changes.markForCheck();
        } else {
          this.notifications.error('offers.sites.errors.refresh', 5000, { site: site.name });
          this.changes.markForCheck();
        }
      }
    });
  }

  private patch(site: string, changes: Partial<OfferSiteHealthEntry>): void {
    if (this.health) {
      this.health = { ...this.health, sites: this.health.sites.map(entry => entry.site === site ? { ...entry, ...changes } : entry) };
    }
    this.changes.markForCheck();
  }
}
