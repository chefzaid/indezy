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
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import {
  DashboardStatsDto, MissionEndingSoon, StaleOpportunity, UpcomingRenewal, OnThisDayItem, DormantContact, FollowUpDue, OverdueInvoice,
  PurchaseOrderAlert, ExpiringDocument, TrainingFundingDue, StatusSuggestion,
  UpcomingInterview
} from '../../../models';

/** The status suggestions dismissed on this device, by exchange id. */
const DISMISSED_KEY = 'indezy.dashboard.dismissedStatusSuggestions';

function readDismissed(): number[] {
  try {
    const stored = JSON.parse(localStorage.getItem(DISMISSED_KEY) ?? '[]');
    return Array.isArray(stored) ? stored.filter((id): id is number => typeof id === 'number') : [];
  } catch {
    return [];
  }
}

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
  private readonly projects = inject(ProjectService);
  private readonly notifications = inject(NotificationService);
  /** The suggestions applied or dismissed, by exchange: dismissed ones are remembered on this device. */
  private readonly handled = new Set<number>(readDismissed());

  /** Opens the AI drafter of the availability announcement. */
  announce(): void {
    this.dialog.open(AiAvailabilityDialogComponent, { width: '720px', maxWidth: '96vw', autoFocus: false });
  }

  /** Opens the prospection emails to the ESN contacts at once, to find the next mission. */
  prospect(): void {
    this.dialog.open(AiAvailabilityDialogComponent, { width: '720px', maxWidth: '96vw', autoFocus: false, data: { prospect: true } });
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
      this.getFollowUpsDue().length + this.getStatusSuggestions().length + this.getOverdueInvoices().length + this.getPurchaseOrderAlerts().length +
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

  getStatusSuggestions(): StatusSuggestion[] {
    return (this.stats?.statusSuggestions ?? []).filter(suggestion => !this.handled.has(suggestion.interactionId));
  }

  /** Moves the opportunity to the stage its contact's words suggest. */
  applySuggestion(suggestion: StatusSuggestion): void {
    this.projects.updateStatus(suggestion.projectId, suggestion.suggested).subscribe({
      next: () => {
        this.handled.add(suggestion.interactionId);
        this.notifications.success('dashboard.suggestions.applied');
      },
      error: () => this.notifications.error('dashboard.suggestions.error')
    });
  }

  /** Leaves the opportunity as it is; the suggestion is not shown again on this device. */
  dismissSuggestion(suggestion: StatusSuggestion): void {
    this.handled.add(suggestion.interactionId);
    try {
      localStorage.setItem(DISMISSED_KEY, JSON.stringify([...readDismissed(), suggestion.interactionId].slice(-200)));
    } catch {
      // Storage refused: the suggestion may show again.
    }
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
