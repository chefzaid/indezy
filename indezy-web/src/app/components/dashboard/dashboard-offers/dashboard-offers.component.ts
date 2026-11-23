import { ChangeDetectionStrategy, ChangeDetectorRef, Component, LOCALE_ID, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, catchError, forkJoin, map, of, switchMap } from 'rxjs';
import { NetworkPost } from '../../../models/network.models';
import { NetworkService } from '../../../services/network/network.service';
import { Offer, OfferMatch, OfferPage } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { defaultOfferQuery } from '../../../services/offer/offer-query';
import { isMatchedSkill, offerDurationKey, offerPlace, offerRate, offerRemoteKey, offerSkillsMatchedFirst } from '../../../shared/utils/offer-format';

/** Offers the widget shows. */
const SHOWN = 5;
/** Skills shown under each offer, the profile's first. */
const SKILLS = 6;
/** A network mission is recommended from this score. */
const MISSION_MIN_SCORE = 35;
/** Points at most that a rate above (or below) the market median adds to (or takes from) an offer's rank. */
const MARKET_POINTS = 5;

/** A recommendation: a collected offer, or a mission a member passes on, ranked by its match and the market. */
export type Recommendation = { kind: 'offer'; offer: Offer; rank: number } | { kind: 'mission'; post: NetworkPost; match: OfferMatch; rank: number };

/**
 * The dashboard's new offers: the five offers of the feed not seen yet that match the profile best, or the five newest
 * when the profile names no skills to match, each with its rate, client, place, remote days, duration, start, the
 * beginning of its description and its skills. With a matching profile, the missions members pass on that fit it best
 * (35 or more, not in the pipeline) join them in one list, each ranked by its score, an offer's moved by up to 5 points
 * by its rate against the market median (see docs/features.md#dashboard-and-analytics). Opening one marks it seen; a
 * failure hides the widget.
 */
@Component({
  selector: 'app-dashboard-offers',
  imports: [DatePipe, MatIconModule, RouterLink, TranslateModule],
  templateUrl: './dashboard-offers.component.html',
  styleUrls: ['./dashboard-offers.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardOffersComponent implements OnInit {
  offers: Offer[] = [];
  /** The offers and the network's missions, best first, five at most. */
  items: Recommendation[] = [];
  /** Whether the offers are the best matches (else the newest). */
  matched = false;
  loaded = false;

  private readonly offerService = inject(OfferService);
  private readonly network = inject(NetworkService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly locale = inject(LOCALE_ID);

  ngOnInit(): void {
    const unseen = { ...defaultOfferQuery(), unseenOnly: true, size: SHOWN };
    this.offerService.searchQuietly({ ...unseen, sort: 'MATCH' }).pipe(
      switchMap(page => page.matchAvailable ? of(page) : this.offerService.searchQuietly({ ...unseen, sort: 'NEWEST' })),
      catchError(() => of(null))
    ).pipe(
      switchMap((page: OfferPage | null) => (page?.matchAvailable ? this.bestMissions() : of([] as Recommendation[]))
        .pipe(map(missions => ({ page, missions }))))
    ).subscribe(({ page, missions }) => {
      this.offers = page?.content ?? [];
      this.matched = !!page?.matchAvailable;
      this.loaded = page !== null;
      const offers: Recommendation[] = this.offers.map(offer => ({ kind: 'offer', offer, rank: rankOf(offer) }));
      this.items = this.matched ? [...offers, ...missions].sort((one, other) => other.rank - one.rank).slice(0, SHOWN) : offers;
      this.changes.markForCheck();
    });
  }

  /** The members' missions fitting the profile best, not in the pipeline; none away from the network. */
  private bestMissions(): Observable<Recommendation[]> {
    return this.network.missions(0, { bestMatch: true, hideTaken: true }).pipe(
      switchMap(page => {
        const posts = page.posts.filter(post => post.kind === 'MISSION').slice(0, SHOWN);
        return posts.length ? forkJoin([of(posts), this.network.missionMatches(posts.map(post => post.id))]) : of([posts, {}] as const);
      }),
      map(([posts, matches]) => posts.flatMap(post => {
        const match = (matches as Record<number, OfferMatch>)[post.id];
        return match && match.score >= MISSION_MIN_SCORE ? [{ kind: 'mission' as const, post, match, rank: match.score }] : [];
      })),
      catchError(() => of([]))
    );
  }

  /** A mission's daily rate range, as its card writes it. */
  missionRate(post: NetworkPost): string | null {
    const details = post.details;
    const low = details?.rateMin ?? null;
    const high = details?.rateMax ?? null;
    if (low === null && high === null) {
      return null;
    }
    const format = (value: number): string => value.toLocaleString(this.locale);
    return low !== null && high !== null && low !== high ?
      `${format(low)}–${format(high)} €` : `${format((high ?? low)!)} €`;
  }

  authorName(post: NetworkPost): string {
    return [post.author.firstName, post.author.lastName].filter(Boolean).join(' ');
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

  remoteKey(offer: Offer): string {
    return offerRemoteKey(offer);
  }

  durationKey(offer: Offer): string {
    return offerDurationKey(offer);
  }

  skills(offer: Offer): string[] {
    return offerSkillsMatchedFirst(offer, SKILLS);
  }

  isMatched(offer: Offer, skill: string): boolean {
    return isMatchedSkill(offer, skill);
  }
}

/** An offer's rank: its score, moved by up to 5 points by its rate against the market (a fifth of the gap). */
export function rankOf(offer: Offer): number {
  const score = offer.match?.score ?? 0;
  const percent = offer.rateMarket?.percent;
  return percent === undefined || percent === null ? score : score + Math.max(-MARKET_POINTS, Math.min(MARKET_POINTS, Math.round(percent / 5)));
}
