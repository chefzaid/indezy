import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { switchMap } from 'rxjs';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { COMMUNITY_NOTICES, MODERATION_NOTICES, NetworkNotificationType, NotificationGroup } from '../../../models';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { TimeAgoPipe } from '../time-ago/time-ago.pipe';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkSideComponent } from '../side/network-side.component';

/** The icon of each kind of notification. */
export const NOTICE_ICONS: Record<NetworkNotificationType, string> = {
  FOLLOW: 'person_add', FOLLOW_REQUEST: 'person_search', FOLLOW_ACCEPTED: 'how_to_reg', LIKE: 'favorite', COMMENT_LIKE: 'favorite', COMMENT: 'chat_bubble', REPLY: 'reply', MENTION: 'alternate_email', REPOST: 'repeat',
  WARNED: 'report', CONTENT_REMOVED: 'delete', SUSPENDED: 'timer_off', REPORT_ACTED: 'gavel', REPORT_DISMISSED: 'flag',
  COMMUNITY_POST: 'diversity_3', COMMUNITY_REQUEST: 'group_add', COMMUNITY_ACCEPTED: 'how_to_reg', COMMUNITY_MODERATOR: 'shield_person', MISSION_MATCH: 'work'
};

/** The filters of the notifications, as on LinkedIn: all, the unread, and by what happened. */
export type NoticeFilter = 'all' | 'unread' | 'mentions' | 'comments' | 'reactions' | 'follows' | 'communities' | 'moderation';
export const NOTICE_FILTERS: NoticeFilter[] = ['all', 'unread', 'mentions', 'comments', 'reactions', 'follows', 'communities', 'moderation'];
const FILTER_TYPES: Partial<Record<NoticeFilter, readonly NetworkNotificationType[]>> = {
  mentions: ['MENTION'], comments: ['COMMENT', 'REPLY'], reactions: ['LIKE', 'COMMENT_LIKE', 'REPOST'],
  follows: ['FOLLOW', 'FOLLOW_REQUEST', 'FOLLOW_ACCEPTED'], communities: COMMUNITY_NOTICES, moderation: MODERATION_NOTICES
};
export const NOTICE_FILTER_ICONS: Record<NoticeFilter, string> = {
  all: 'notifications', unread: 'mark_email_unread', mentions: 'alternate_email', comments: 'chat_bubble', reactions: 'favorite',
  follows: 'person_add', communities: 'diversity_3', moderation: 'shield'
};

/**
 * The member's notifications (`/network/notifications`), grouped as they read ("Léo and 2 others liked your post"),
 * the unread ones marked; opening the page marks them read.
 */
@Component({
  selector: 'app-network-notifications',
  imports: [NetworkNavComponent, NetworkMeCardComponent, NetworkSideComponent, MatButtonModule, MatMenuModule, DatePipe, NgTemplateOutlet, MemberAvatarComponent, TimeAgoPipe, RouterLink, MatIconModule, TranslateModule, PageHeaderComponent],
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
  readonly filters = NOTICE_FILTERS;
  readonly filterIcons = NOTICE_FILTER_ICONS;
  /** The filter chosen, the menu on the left (chips above the list on a narrower screen). */
  filter: NoticeFilter = 'all';

  ngOnInit(): void {
    this.network.notifications().subscribe({
      next: groups => {
        this.groups = groups;
        this.sections = NetworkNotificationsComponent.byPeriod(this.shown(), new Date());
        this.loaded = true;
        this.changes.markForCheck();
        if (groups.some(group => !group.read)) {
          this.network.readNotifications().subscribe({ next: () => this.notices.refresh(), error: () => undefined });
        }
      },
      error: () => this.notifications.error('network.notices.loadError')
    });
  }

  /** Shows the notifications of a kind only, or all of them. */
  choose(filter: NoticeFilter): void {
    this.filter = filter;
    this.sections = NetworkNotificationsComponent.byPeriod(this.shown(), new Date());
  }

  /** How many notifications the filter holds, unread ones for **Unread**. */
  count(filter: NoticeFilter): number {
    return this.groups.filter(group => NetworkNotificationsComponent.matches(group, filter)).length;
  }

  private shown(): NotificationGroup[] {
    return this.groups.filter(group => NetworkNotificationsComponent.matches(group, this.filter));
  }

  static matches(group: NotificationGroup, filter: NoticeFilter): boolean {
    if (filter === 'all') {
      return true;
    }
    return filter === 'unread' ? !group.read : (FILTER_TYPES[filter] ?? []).includes(group.type);
  }

  /** Whether any notification shows unread. */
  get anyUnread(): boolean {
    return this.groups.some(group => !group.read);
  }

  /** Marks every notification read: the tints and dots go, and the counts. */
  markAllRead(): void {
    this.network.readNotifications().subscribe({
      next: () => {
        this.groups = this.groups.map(group => ({ ...group, read: true }));
        this.sections = NetworkNotificationsComponent.byPeriod(this.shown(), new Date());
        this.notices.refresh();
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.notices.actionError')
    });
  }

  /** Deletes a notification (its group). */
  dismiss(group: NotificationGroup): void {
    this.network.dismissNotification(group.type, group.postId).subscribe({
      next: () => {
        this.groups = this.groups.filter(item => item.type !== group.type || item.postId !== group.postId);
        this.sections = NetworkNotificationsComponent.byPeriod(this.shown(), new Date());
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.notices.actionError')
    });
  }

  /** Turns its kind off, as Profile → Network does: no more of these, in the app nor by email. */
  turnOff(group: NotificationGroup): void {
    this.network.notificationSettings().pipe(switchMap(settings => this.network.saveNotificationSettings(
      [...settings.off.filter(type => type !== group.type), group.type], (settings.emailed ?? []).filter(type => type !== group.type))))
      .subscribe({
        next: () => this.notifications.success('network.notices.turnedOff'),
        error: () => this.notifications.error('network.notices.actionError')
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
    if (group.community) {
      return ['/network/communities', group.community.slug];
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
