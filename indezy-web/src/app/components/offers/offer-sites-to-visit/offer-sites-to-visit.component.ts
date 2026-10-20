import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, LOCALE_ID, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { OfferLeftOutKind, OfferLeftOutSite } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { relativeTime } from '../../../shared/utils/relative-time';

const GROUPS: OfferLeftOutKind[] = ['VISIT', 'MATCHING'];

/**
 * The sites Indezy does not read but where a freelancer finds missions independently: those to see in person (an
 * account, a membership or a referral, or closed to crawlers) and the matching services, with what each takes and when
 * the workspace last visited it. Opening a site records the visit.
 */
@Component({
  selector: 'app-offer-sites-to-visit',
  imports: [MatButtonModule, MatIconModule, TranslateModule],
  templateUrl: './offer-sites-to-visit.component.html',
  styleUrls: ['./offer-sites-to-visit.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OfferSitesToVisitComponent {
  groups: { kind: OfferLeftOutKind; sites: OfferLeftOutSite[] }[] = [];
  readonly saving = new Set<string>();

  private readonly offerService = inject(OfferService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly locale = inject(LOCALE_ID);

  /** Every site Indezy does not read; those left out are not shown here. */
  @Input({ required: true }) set sites(sites: OfferLeftOutSite[]) {
    this.groups = GROUPS.map(kind => ({ kind, sites: sites.filter(site => site.kind === kind) })).filter(group => group.sites.length);
  }

  when(moment: string): string {
    return relativeTime(moment, this.locale);
  }

  /** The site's link was followed: that is a visit. */
  opened(site: OfferLeftOutSite): void {
    this.markVisited(site);
  }

  markVisited(site: OfferLeftOutSite): void {
    this.save(site, new Date().toISOString(), () => this.offerService.visitSite(site.site));
  }

  clear(site: OfferLeftOutSite): void {
    this.save(site, null, () => this.offerService.clearSiteVisit(site.site));
  }

  /** Shows the change at once, keeps the server's time, and puts the former one back when it fails. */
  private save(site: OfferLeftOutSite, visitedAt: string | null,
               request: () => ReturnType<OfferService['visitSite']>): void {
    const before = site.visitedAt ?? null;
    this.saving.add(site.site);
    this.patch(site.site, visitedAt);
    request().subscribe({
      next: answer => {
        this.saving.delete(site.site);
        this.patch(site.site, answer.visitedAt);
      },
      error: () => {
        this.saving.delete(site.site);
        this.patch(site.site, before);
        this.notifications.error('offers.sites.visit.error');
      }
    });
  }

  private patch(siteId: string, visitedAt: string | null): void {
    this.groups = this.groups.map(group => ({
      ...group,
      sites: group.sites.map(site => site.site === siteId ? { ...site, visitedAt } : site)
    }));
    this.changes.markForCheck();
  }
}
