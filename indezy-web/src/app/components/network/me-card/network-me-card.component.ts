import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { switchMap } from 'rxjs';

import { MemberPage } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { MemberAvatarComponent, memberGradient } from '../avatar/member-avatar.component';

/** The width below which the feed is one column, the card a strip above the posts (network-me-card.component.scss). */
const NARROW = '(max-width: 1099.98px)';

/**
 * The reader's own page, beside the feed (as on LinkedIn): their cover, avatar, name and headline, how many follow them
 * and whom they follow, and their page's main tabs (posts, saved, drafts), each one click away.
 */
@Component({
  selector: 'app-network-me-card',
  imports: [RouterLink, MatIconModule, TranslateModule, MemberAvatarComponent],
  templateUrl: './network-me-card.component.html',
  styleUrls: ['./network-me-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkMeCardComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly changes = inject(ChangeDetectorRef);

  /** The reader's page; none while it loads, or for a visitor not in the network. */
  page: MemberPage | null = null;

  ngOnInit(): void {
    this.network.membership(true).pipe(switchMap(membership => this.network.page(membership.handle))).subscribe({
      next: page => {
        this.page = page.own ? page : null;
        this.changes.markForCheck();
      },
      error: () => this.page = null
    });
  }

  /** The avatar's size: smaller in the strip a narrow screen shows. */
  get avatarSize(): number {
    return typeof window !== 'undefined' && window.matchMedia(NARROW).matches ? 40 : 64;
  }

  get cover(): string {
    return memberGradient(this.page?.card.handle ?? '', 120);
  }
}
