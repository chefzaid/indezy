import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { MODERATION_NOTICES, NetworkNotificationType, NotificationGroup } from '../../../models';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { TimeAgoPipe } from '../time-ago/time-ago.pipe';

/** The icon of each kind of notification. */
export const NOTICE_ICONS: Record<NetworkNotificationType, string> = {
  FOLLOW: 'person_add', FOLLOW_REQUEST: 'person_search', FOLLOW_ACCEPTED: 'how_to_reg', LIKE: 'favorite', COMMENT_LIKE: 'favorite', COMMENT: 'chat_bubble', REPLY: 'reply', MENTION: 'alternate_email', REPOST: 'repeat',
  WARNED: 'report', CONTENT_REMOVED: 'delete', SUSPENDED: 'timer_off', REPORT_ACTED: 'gavel', REPORT_DISMISSED: 'flag'
};

/**
 * The member's notifications (`/network/notifications`), grouped as they read ("Léo and 2 others liked your post"),
 * the unread ones marked; opening the page marks them read.
 */
@Component({
  selector: 'app-network-notifications',
  imports: [DatePipe, NgTemplateOutlet, MemberAvatarComponent, TimeAgoPipe, RouterLink, MatIconModule, TranslateModule, PageHeaderComponent],
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
  /** The groups by period: today, the last 7 days, earlier; empty periods left out. */
  sections: { key: 'today' | 'week' | 'earlier'; groups: NotificationGroup[] }[] = [];
  loaded = false;

  ngOnInit(): void {
    this.network.notifications().subscribe({
      next: groups => {
        this.groups = groups;
        this.sections = NetworkNotificationsComponent.byPeriod(groups, new Date());
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

  /** Where the notification leads: the post it is about, else the member who did it; nowhere for the moderators' word. */
  link(group: NotificationGroup): (string | number)[] | null {
    if (this.moderation(group)) {
      return null;
    }
    if (group.postId !== null) {
      return ['/network/posts', group.postId];
    }
    return group.actors.length ? ['/m', group.actors[0].handle] : null;
  }

  /** The groups by period, as they came: today's, the last 7 days', then the earlier ones. */
  static byPeriod(groups: NotificationGroup[], now: Date): { key: 'today' | 'week' | 'earlier'; groups: NotificationGroup[] }[] {
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const week = today - 6 * 24 * 60 * 60 * 1000;
    const sections: { key: 'today' | 'week' | 'earlier'; groups: NotificationGroup[] }[] = [
      { key: 'today', groups: [] }, { key: 'week', groups: [] }, { key: 'earlier', groups: [] }];
    for (const group of groups) {
      const at = new Date(group.latestAt).getTime();
      sections[at >= today ? 0 : at >= week ? 1 : 2].groups.push(group);
    }
    return sections.filter(section => section.groups.length > 0);
  }
}
