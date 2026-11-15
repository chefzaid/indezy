import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, Subject, of, switchMap, takeUntil } from 'rxjs';

import { ModerationQueue, NetworkReport, ReportOutcome, ReportParty } from '../../models';
import { AdminService } from '../../services/admin/admin.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';

/** The days a suspension lasts unless the moderator says otherwise. */
export const DEFAULT_SUSPENSION_DAYS = 7;

/**
 * The moderation queue of the member network: the open reports, the oldest first, each decided with a note for the
 * audit trail (dismiss, warn, remove, suspend, ban), the decision falling on every open report of the same target; then
 * the latest decisions.
 */
@Component({
  selector: 'app-admin-moderation',
  imports: [DatePipe, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule],
  templateUrl: './admin-moderation.component.html',
  styleUrls: ['./admin-moderation.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AdminModerationComponent implements OnInit, OnDestroy {
  private readonly admin = inject(AdminService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly destroy$ = new Subject<void>();

  queue: ModerationQueue | null = null;
  /** The note and the days of suspension typed for each open report. */
  readonly notes: Record<number, string> = {};
  readonly days: Record<number, number> = {};
  deciding: number | null = null;

  ngOnInit(): void {
    this.admin.reports().pipe(takeUntil(this.destroy$)).subscribe({
      next: queue => this.queue = queue,
      error: () => this.notifications.error('admin.loadError')
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** The outcomes open to the report: a member is suspended or banned, never removed. */
  outcomes(report: NetworkReport): ReportOutcome[] {
    return report.targetType === 'MEMBER' ? ['DISMISSED', 'WARNED', 'SUSPENDED', 'BANNED'] :
      ['DISMISSED', 'WARNED', 'REMOVED', 'SUSPENDED', 'BANNED'];
  }

  /** The handle, else the name, else the account id of someone gone. */
  who(party: ReportParty | null): string {
    if (!party) {
      return '';
    }
    return party.handle ? `@${party.handle}` : party.name ?? `#${party.accountId}`;
  }

  setNote(report: NetworkReport, value: string): void {
    this.notes[report.id] = value;
  }

  setDays(report: NetworkReport, value: string): void {
    this.days[report.id] = Number(value);
  }

  daysOf(report: NetworkReport): number {
    return this.days[report.id] ?? DEFAULT_SUSPENSION_DAYS;
  }

  /** Decides the report; a ban once confirmed. */
  decide(report: NetworkReport, outcome: ReportOutcome): void {
    const days = this.daysOf(report);
    if (outcome === 'SUSPENDED' && !(Number.isInteger(days) && days >= 1 && days <= 90)) {
      this.notifications.error('admin.moderation.daysInvalid');
      return;
    }
    const confirmed: Observable<boolean> = outcome === 'BANNED' ? this.confirm.confirm({ messageKey: 'admin.moderation.banConfirm',
      messageParams: { who: this.who(report.target) }, confirmKey: 'admin.moderation.outcomes.BANNED', danger: true }) : of(true);
    confirmed.pipe(
      switchMap(yes => {
        if (!yes) {
          return of(null);
        }
        this.deciding = report.id;
        return this.admin.decide(report.id, outcome, this.notes[report.id]?.trim() || null, outcome === 'SUSPENDED' ? days : null);
      }),
      takeUntil(this.destroy$)
    ).subscribe({
      next: queue => {
        this.deciding = null;
        if (queue) {
          this.queue = queue;
          this.notifications.success('admin.moderation.decided');
        }
      },
      error: () => {
        this.deciding = null;
        this.notifications.error('admin.saveError');
      }
    });
  }
}
