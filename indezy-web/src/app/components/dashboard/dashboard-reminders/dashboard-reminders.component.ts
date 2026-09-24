import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { DashboardStatsDto, MissionEndingSoon, StaleOpportunity, UpcomingRenewal, OnThisDayItem, DormantContact } from '../../../models';

/**
 * Renders the dashboard's time-based reminder lists (missions ending, renewals, "on this day",
 * dormant contacts, stale opportunities) from the aggregated dashboard stats.
 */
@Component({
  selector: 'app-dashboard-reminders',
  imports: [CommonModule, RouterModule, MatIconModule, TranslateModule],
  templateUrl: './dashboard-reminders.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./dashboard-reminders.component.scss']
})
export class DashboardRemindersComponent {
  @Input() stats: DashboardStatsDto | null = null;

  /** Reminder lists show this many rows until the user expands them. */
  static readonly COLLAPSED_SIZE = 5;
  readonly collapsedSize = DashboardRemindersComponent.COLLAPSED_SIZE;
  private readonly expanded = new Set<string>();

  /** The rows of a reminder list to render, capped unless the list was expanded. */
  visible<T>(list: T[], key: string): T[] {
    return this.expanded.has(key) ? list : list.slice(0, DashboardRemindersComponent.COLLAPSED_SIZE);
  }

  isExpanded(key: string): boolean {
    return this.expanded.has(key);
  }

  toggle(key: string): void {
    if (this.expanded.has(key)) {
      this.expanded.delete(key);
    } else {
      this.expanded.add(key);
    }
  }

  hasReminders(): boolean {
    return this.getMissionsEndingSoon().length + this.getUpcomingRenewals().length +
      this.getOnThisDay().length + this.getDormantContacts().length + this.getStaleOpportunities().length > 0;
  }

  getMissionsEndingSoon(): MissionEndingSoon[] {
    return this.stats?.missionsEndingSoon ?? [];
  }

  getUpcomingRenewals(): UpcomingRenewal[] {
    return this.stats?.upcomingRenewals ?? [];
  }

  getOnThisDay(): OnThisDayItem[] {
    return this.stats?.onThisDay ?? [];
  }

  getDormantContacts(): DormantContact[] {
    return this.stats?.dormantContacts ?? [];
  }

  getStaleOpportunities(): StaleOpportunity[] {
    return this.stats?.staleOpportunities ?? [];
  }

  /** Router path for an "on this day" item, based on whether it is a project or a contact. */
  getOnThisDayLink(item: OnThisDayItem): unknown[] {
    return item.type === 'CONTACT' ? ['/contacts', item.id] : ['/projects', item.id];
  }
}
