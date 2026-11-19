import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { PageViews } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { TimeAgoPipe } from '../time-ago/time-ago.pipe';

/**
 * **Who viewed my page**, a tab of the reader's own page: how many members opened it in the last 90 days and how often
 * this week, those named (the latest first) and how many browsed privately; **Browse privately** hides the reader's own
 * visits, and with them the names of their viewers.
 */
@Component({
  selector: 'app-network-viewers',
  imports: [DatePipe, RouterLink, MatIconModule, MatSlideToggleModule, TranslateModule, MemberAvatarComponent, TimeAgoPipe],
  templateUrl: './network-viewers.component.html',
  styleUrls: ['./network-viewers.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkViewersComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  views: PageViews | null = null;
  saving = false;

  ngOnInit(): void {
    this.network.pageViews().subscribe({
      next: views => {
        this.views = views;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.viewers.loadError')
    });
  }

  setPrivate(change: MatSlideToggleChange): void {
    this.saving = true;
    this.network.setPrivateViewing(change.checked).subscribe({
      next: views => {
        this.views = views;
        this.saving = false;
        this.changes.markForCheck();
      },
      error: () => {
        change.source.checked = !change.checked;
        this.saving = false;
        this.notifications.error('network.viewers.saveError');
        this.changes.markForCheck();
      }
    });
  }
}
