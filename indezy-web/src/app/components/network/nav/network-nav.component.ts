import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkService } from '../../../services/network/network.service';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkBackComponent } from '../back/network-back.component';

/**
 * The network's sections, at the top of each of its pages as a row of tabs (Home, Notifications and Messages with what
 * is unread, Members, Saved, Drafts, My page), the one open marked: a section is one click away from any other. A page
 * reached from another (a post, a tag, a member) also opens the row with **Back**.
 */
@Component({
  selector: 'app-network-nav',
  imports: [AsyncPipe, RouterLink, RouterLinkActive, MatIconModule, TranslateModule, NetworkBackComponent],
  templateUrl: './network-nav.component.html',
  styleUrls: ['./network-nav.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkNavComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly changes = inject(ChangeDetectorRef);
  readonly unread$ = inject(NetworkNoticesService).unread$;
  readonly unreadMessages$ = inject(NetworkNoticesService).messages$;

  /** Opens the row with Back, on a page reached from another. */
  @Input() back = false;
  /** The reader's handle, for My page; none while unknown or for a visitor not in the network. */
  handle: string | null = null;

  ngOnInit(): void {
    this.network.membership(true).subscribe({
      next: membership => {
        this.handle = membership.joined ? membership.handle : null;
        this.changes.markForCheck();
      },
      error: () => this.handle = null
    });
  }
}
