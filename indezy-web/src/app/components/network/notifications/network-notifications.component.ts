import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { MODERATION_NOTICES, NetworkNotificationType, NotificationGroup } from '../../../models';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';

/** The icon of each kind of notification. */
export const NOTICE_ICONS: Record<NetworkNotificationType, string> = {
  FOLLOW: 'person_add', LIKE: 'favorite', COMMENT_LIKE: 'favorite', COMMENT: 'chat_bubble', REPLY: 'reply', MENTION: 'alternate_email', REPOST: 'repeat',
  WARNED: 'report', CONTENT_REMOVED: 'delete', SUSPENDED: 'timer_off', REPORT_ACTED: 'gavel', REPORT_DISMISSED: 'flag'
};

/**
 * The member's notifications (`/network/notifications`), grouped as they read ("Léo and 2 others liked your post"),
 * the unread ones marked; opening the page marks them read.
 */
@Component({
  selector: 'app-network-notifications',
  imports: [DatePipe, RouterLink, MatIconModule, TranslateModule, PageHeaderComponent],
  templateUrl: './network-notifications.component.html',
  styleUrls: ['./network-notifications.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkNotificationsComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notices = inject(NetworkNoticesService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly icons = NOTICE_ICONS;
  groups: NotificationGroup[] = [];
  loaded = false;

  ngOnInit(): void {
    this.network.notifications().subscribe({
      next: groups => {
        this.groups = groups;
        this.loaded = true;
        this.changes.markForCheck();
        if (groups.some(group => !group.read)) {
          this.network.readNotifications().subscribe({ next: () => this.notices.refresh(), error: () => undefined });
        }
      },
      error: () => this.notifications.error('network.notices.loadError')
    });
  }

  /** Whether the moderators, not a member, tell it. */
  moderation(group: NotificationGroup): boolean {
    return MODERATION_NOTICES.includes(group.type);
  }

  /** "Léo", "Léo and Inès", or "Léo and 3 others": who did it. */
  who(group: NotificationGroup): { key: string; params: Record<string, unknown> } {
    const [first, second] = group.actors;
    if (group.count === 1) {
      return { key: 'network.notices.one', params: { name: first.firstName } };
    }
    return group.count === 2 ? { key: 'network.notices.two', params: { name: first.firstName, other: second.firstName } } :
      { key: 'network.notices.many', params: { name: first.firstName, others: group.count - 1 } };
  }
}
