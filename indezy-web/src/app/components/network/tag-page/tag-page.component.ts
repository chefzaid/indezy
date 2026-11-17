import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { map, switchMap } from 'rxjs';

import { NetworkPost, Offer, TagPage } from '../../../models';
import { OfferService } from '../../../services/offer/offer.service';
import { defaultOfferQuery, offerQueryToParams } from '../../../services/offer/offer-query';
import { CapabilitiesService } from '../../../services/admin/capabilities.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { PostCardComponent } from '../post-card/post-card.component';

/** How many of the skill's open missions the page shows. */
const MISSIONS_SHOWN = 5;

/**
 * A tag's page (`/network/tags/<tag>`): following it, the posts naming it, the members whose cards show the skill and,
 * while the offer feed is on, its newest open missions for the skill.
 */
@Component({
  selector: 'app-tag-page',
  imports: [MemberAvatarComponent, MatButtonToggleModule, PageHeaderComponent, RouterLink, MatButtonModule, MatIconModule, TranslateModule, PostCardComponent],
  templateUrl: './tag-page.component.html',
  styleUrls: ['./tag-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TagPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly offers = inject(OfferService);
  private readonly capabilities = inject(CapabilitiesService);

  page: TagPage | null = null;
  posts: NetworkPost[] = [];
  /** The posts the newest first, or the most liked (whatever the reaction) first. */
  sort: 'recent' | 'liked' = 'recent';
  /** The feed's newest open missions for the skill; none asked while the offer feed is off. */
  missions: Offer[] | null = null;
  /** The offer feed's address for every mission of the skill. */
  allMissions: Record<string, unknown> = {};

  ngOnInit(): void {
    this.route.paramMap.pipe(map(params => params.get('tag') ?? ''), switchMap(tag => this.network.tagPage(tag)),
      takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => this.show(page),
      error: () => this.notifications.error('network.tags.loadError')
    });
  }

  more(): void {
    this.network.tagPage(this.page!.tag, this.page!.posts.page + 1, this.sort).subscribe({
      next: page => {
        this.posts = [...this.posts, ...page.posts.posts];
        this.page = page;
      },
      error: () => this.notifications.error('network.tags.loadError')
    });
  }

  /** Lists the posts in the other order, from the first page. */
  order(sort: 'recent' | 'liked'): void {
    if (sort === this.sort) {
      return;
    }
    this.sort = sort;
    this.network.tagPage(this.page!.tag, 0, sort).subscribe({
      next: page => this.show(page),
      error: () => this.notifications.error('network.tags.loadError')
    });
  }

  toggleFollow(): void {
    const page = this.page!;
    this.network.followTag(page.tag, !page.followed).subscribe({
      next: tags => this.page = { ...page, followed: tags.includes(page.tag) },
      error: () => this.notifications.error('network.tags.followError')
    });
  }

  /** The rate range of an offer, in euros a day. */
  rate(offer: Offer): string {
    const low = offer.rateMin ?? offer.rateMax;
    const high = offer.rateMax ?? offer.rateMin;
    return low === high ? `${low} €` : `${low}–${high} €`;
  }

  private show(page: TagPage): void {
    const changed = this.page?.tag !== page.tag;
    this.page = page;
    this.posts = page.posts.posts;
    if (changed) {
      this.loadMissions(page.tag);
    }
    this.changes.markForCheck();
  }

  /** The offer feed's newest missions naming the skill, while the feed is on; its failure leaves the section out. */
  private loadMissions(tag: string): void {
    if (this.capabilities.snapshot().features.OFFER_FEED !== true) {
      this.missions = null;
      return;
    }
    const query = { ...defaultOfferQuery(), skills: [tag], sort: 'NEWEST' as const, size: MISSIONS_SHOWN };
    this.allMissions = offerQueryToParams({ ...query, size: defaultOfferQuery().size });
    this.offers.searchQuietly(query).subscribe({
      next: found => {
        this.missions = found.content;
        this.changes.markForCheck();
      },
      error: () => this.missions = null
    });
  }
}
