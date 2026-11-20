import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, forkJoin, of } from 'rxjs';

import { NetworkAbout, NetworkFields, NetworkMembership } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';

/** What a card shows to be found: each with whether it does, and where to fix it. */
export type StrengthItem = 'photo' | 'headline' | 'skills' | 'city' | 'availability' | 'about';
const ITEMS: StrengthItem[] = ['photo', 'headline', 'skills', 'city', 'availability', 'about'];
/** The card field that hides each item, when there is one. */
const FIELDS: Partial<Record<StrengthItem, keyof NetworkFields>> = { photo: 'photo', headline: 'headline', skills: 'stack', city: 'location',
  availability: 'availability' };
/** How many skills a card shows to be found by its stack. */
export const SKILLS_WANTED = 3;

/** A missing item: what it is, whether it is only hidden (shown from Profile → Network), and where to fix it. */
export interface StrengthGap {
  item: StrengthItem;
  hidden: boolean;
  tab: 'network' | null;
}

/**
 * **Card strength**, beside the reader's own page: how much of what members search their card shows (a photo, a
 * headline, three skills, a city, an availability, an About section), and each missing item one click from where it is
 * set: the profile for what it holds, Profile → Network for what the card hides or adds.
 */
@Component({
  selector: 'app-card-strength',
  imports: [RouterLink, MatIconModule, TranslateModule],
  templateUrl: './card-strength.component.html',
  styleUrls: ['./card-strength.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CardStrengthComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly changes = inject(ChangeDetectorRef);

  /** The share of the items the card shows, from 0 to 100; none until read. */
  percent: number | null = null;
  gaps: StrengthGap[] = [];

  ngOnInit(): void {
    forkJoin([this.network.membership(true), this.network.myAbout().pipe(catchError(() => of({ about: null, showMissions: false })))])
      .subscribe({
        next: ([membership, about]) => {
          if (membership.joined && membership.card) {
            this.gaps = CardStrengthComponent.gaps(membership, about);
            this.percent = Math.round(100 * (ITEMS.length - this.gaps.length) / ITEMS.length);
          }
          this.changes.markForCheck();
        },
        error: () => this.percent = null
      });
  }

  /** What the card lacks, in the order members look for it. */
  static gaps(membership: NetworkMembership, about: NetworkAbout): StrengthGap[] {
    const card = membership.card!;
    const shows: Record<StrengthItem, boolean> = {
      photo: !!card.avatar, headline: !!card.headline, skills: card.stack.length >= SKILLS_WANTED, city: !!card.city,
      availability: !!card.availability, about: !!about.about?.trim()
    };
    return ITEMS.filter(item => !shows[item]).map(item => {
      const field = FIELDS[item];
      const hidden = field !== undefined && !membership.fields[field];
      return { item, hidden, tab: hidden || item === 'about' || item === 'availability' ? 'network' : null };
    });
  }

  /** "To complete", "Good" or "Complete". */
  get level(): string {
    const percent = this.percent ?? 0;
    return percent === 100 ? 'complete' : percent >= 60 ? 'good' : 'low';
  }
}
