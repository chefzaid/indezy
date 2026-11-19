import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkBackComponent } from '../back/network-back.component';
import { NetworkSearchComponent } from '../search/network-search.component';

/**
 * The network's sections, at the top of each of its pages: its search, then a row of tabs (Home, Messages, Members,
 * Notifications, with what is unread), the one open marked, so that a section is one click away from any other. A page
 * reached from another (a post, a tag, a member) opens the row with **Back**. The reader's own page, with their saved
 * posts and drafts, is the card beside the feed.
 */
@Component({
  selector: 'app-network-nav',
  imports: [AsyncPipe, RouterLink, RouterLinkActive, MatIconModule, TranslateModule, NetworkBackComponent, NetworkSearchComponent],
  templateUrl: './network-nav.component.html',
  styleUrls: ['./network-nav.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkNavComponent {
  readonly unread$ = inject(NetworkNoticesService).unread$;
  readonly unreadMessages$ = inject(NetworkNoticesService).messages$;

  /** Opens the row with Back, on a page reached from another. */
  @Input() back = false;
}
