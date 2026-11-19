import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { MemberSuggestion, TrendingTag } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { suggestionReason } from './suggestion-reason';

/** How many members to follow the column shows. */
const SUGGESTED = 4;
/** How many trending tags it shows. */
const TRENDING = 5;

/**
 * The right column of the network's pages but the feed (a member's page, the notifications, the search), as on
 * LinkedIn: members to follow, each with its reason and **Follow**, and the tags trending this week; each card left out
 * while empty. A member already on the page (`exclude`) is not suggested.
 */
@Component({
  selector: 'app-network-side',
  imports: [RouterLink, MatButtonModule, TranslateModule, MemberAvatarComponent],
  templateUrl: './network-side.component.html',
  styleUrls: ['./network-side.component.scss', '../feed/network-feed-side.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkSideComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  /** The handle of the member whose page this is, kept out of the suggestions. */
  @Input() exclude: string | null = null;
  /** A member followed from the column. */
  @Output() readonly followed = new EventEmitter<string>();

  suggestions: MemberSuggestion[] = [];
  trending: TrendingTag[] = [];

  ngOnInit(): void {
    // Cards beside the page: when one cannot be read, it is left out without a word.
    this.network.suggestions(true).subscribe({
      next: suggestions => {
        this.suggestions = suggestions.filter(item => item.card.handle !== this.exclude).slice(0, SUGGESTED);
        this.changes.markForCheck();
      },
      error: () => this.suggestions = []
    });
    this.network.trendingTags(true).subscribe({
      next: trending => {
        this.trending = trending.slice(0, TRENDING);
        this.changes.markForCheck();
      },
      error: () => this.trending = []
    });
  }

  reason(suggestion: MemberSuggestion): string {
    return suggestionReason(this.translate, suggestion);
  }

  /** Follows a suggested member, who leaves the card. */
  follow(suggestion: MemberSuggestion): void {
    this.network.follow(suggestion.card.handle).subscribe({
      next: () => {
        this.suggestions = this.suggestions.filter(item => item !== suggestion);
        this.followed.emit(suggestion.card.handle);
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) =>
        this.notifications.error(error.status === 429 ? 'network.page.tooManyFollows' : 'network.page.followError')
    });
  }
}
