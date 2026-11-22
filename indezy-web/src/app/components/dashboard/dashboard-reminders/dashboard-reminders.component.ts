import { Component, Input, ChangeDetectionStrategy, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { AiAvailabilityDialogComponent } from '../../../shared/components/ai-availability-dialog/ai-availability-dialog.component';
import { AiMessageDialogComponent, AiMessageDialogData } from '../../../shared/components/ai-message-dialog/ai-message-dialog.component';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import {
  DashboardStatsDto, MissionEndingSoon, StaleOpportunity, UpcomingRenewal, OnThisDayItem, DormantContact, FollowUpDue, OverdueInvoice,
  PurchaseOrderAlert, ExpiringDocument, TrainingFundingDue,
  UpcomingInterview
} from '../../../models';

/**
 * Renders the dashboard's time-based reminder lists (interviews of the week, missions ending, renewals, "on this day",
 * dormant contacts, stale opportunities, overdue invoices) from the aggregated dashboard stats.
 */
@Component({
  selector: 'app-dashboard-reminders',
  imports: [CommonModule, RouterModule, MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule],
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
  private readonly dialog = inject(MatDialog);

  /** Opens the AI drafter of the availability announcement. */
  announce(): void {
    this.dialog.open(AiAvailabilityDialogComponent, { width: '720px', maxWidth: '96vw', autoFocus: false });
  }

  /** Drafts the follow-up of a message waiting for an answer, from the opportunity it was about. */
  draftFollowUp(due: FollowUpDue): void {
    if (!due.projectId) {
      return;
    }
    const data: AiMessageDialogData = { projectId: due.projectId, kind: 'FOLLOW_UP' };
    this.dialog.open(AiMessageDialogComponent, { data, width: '720px', maxWidth: '96vw', autoFocus: false });
  }

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
    return !!this.stats?.benchPlan || this.getUpcomingInterviews().length + this.getMissionsEndingSoon().length + this.getUpcomingRenewals().length +
      this.getOnThisDay().length + this.getDormantContacts().length + this.getStaleOpportunities().length +
      this.getFollowUpsDue().length + this.getOverdueInvoices().length + this.getPurchaseOrderAlerts().length +
      this.getExpiringDocuments().length + this.getTrainingFundingDue().length > 0;
  }

  /** The share of this week's goal reached, for the bar. */
  benchProgress(plan: { addedThisWeek: number; weeklyGoal: number }): number {
    return plan.weeklyGoal > 0 ? Math.min(100, Math.round(plan.addedThisWeek * 100 / plan.weeklyGoal)) : 100;
  }

  getUpcomingInterviews(): UpcomingInterview[] {
    return this.stats?.upcomingInterviews ?? [];
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

  getFollowUpsDue(): FollowUpDue[] {
    return this.stats?.followUpsDue ?? [];
  }

  getOverdueInvoices(): OverdueInvoice[] {
    return this.stats?.overdueInvoices ?? [];
  }

  getPurchaseOrderAlerts(): PurchaseOrderAlert[] {
    return this.stats?.purchaseOrderAlerts ?? [];
  }

  getExpiringDocuments(): ExpiringDocument[] {
    return this.stats?.expiringDocuments ?? [];
  }

  getTrainingFundingDue(): TrainingFundingDue[] {
    return this.stats?.trainingFundingDue ?? [];
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
