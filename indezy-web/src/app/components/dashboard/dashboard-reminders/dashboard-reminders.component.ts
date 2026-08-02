import { Component, Input } from '@angular/core';
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
  styleUrls: ['./dashboard-reminders.component.scss']
})
export class DashboardRemindersComponent {
  @Input() stats: DashboardStatsDto | null = null;

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
