import {
  ChangeDetectionStrategy,
  Component,
  Input,
  LOCALE_ID,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  inject
} from '@angular/core';
import { DOCUMENT, DecimalPipe, Location, formatDate, formatNumber } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Clipboard } from '@angular/cdk/clipboard';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, Subject, of, switchMap, takeUntil } from 'rxjs';

import {
  ACTIVITY_REPORT_STATUS_COLORS,
  ActivityReportDay,
  ActivityReport,
  ActivityReportLink,
  ProjectDto
} from '../../../models';
import { ActivityReportService } from '../../../services/activity-report/activity-report.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { ActivityCalendarComponent } from '../../../shared/components/activity-calendar/activity-calendar.component';
import {
  DAY_BRUSHES,
  DayBrush,
  applyBrush,
  fillBusinessDays,
  totals
} from '../../../shared/components/activity-calendar/activity-calendar.util';
import { saveBlob } from '../../../shared/utils/download-file';

const BRUSH_ICONS: Record<DayBrush, string> = {
  ONSITE: 'business',
  REMOTE: 'home',
  HALF_ONSITE: 'business',
  HALF_REMOTE: 'home',
  ABSENCE: 'event_busy',
  HALF_ABSENCE: 'event_busy',
  CLEAR: 'backspace'
};

/**
 * The monthly activity reports (CRA) of an accepted mission: record the month's days, send it to the client or ESN
 * through a validation link, follow its approval, and download it as PDF.
 */
@Component({
  selector: 'app-activity-report-card',
  imports: [
    DecimalPipe,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
    TranslateModule,
    ActivityCalendarComponent
  ],
  templateUrl: './activity-report-card.component.html',
  styleUrls: ['./activity-report-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ActivityReportCardComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) project!: ProjectDto;

  private readonly reports = inject(ActivityReportService);
  private readonly notifications = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly translate = inject(TranslateService);
  private readonly clipboard = inject(Clipboard);
  private readonly location = inject(Location);
  private readonly document = inject(DOCUMENT);
  private readonly locale = inject(LOCALE_ID);
  private readonly destroy$ = new Subject<void>();

  readonly brushes = DAY_BRUSHES;
  readonly brushIcons = BRUSH_ICONS;
  readonly statusColors = ACTIVITY_REPORT_STATUS_COLORS;

  year = 0;
  month = 0;
  report?: ActivityReport;
  /** The days being edited, saved with the comment. */
  days: ActivityReportDay[] = [];
  comment = '';
  savedReports: ActivityReport[] = [];
  brush: DayBrush = 'ONSITE';
  dirty = false;
  isLoading = false;
  isBusy = false;
  /** The validation link, known only right after it was issued. */
  link?: string;
  linkExpiresAt?: string;

  ngOnChanges(changes: SimpleChanges): void {
    const previous = changes['project']?.previousValue as ProjectDto | undefined;
    if (!this.project?.id || previous?.id === this.project.id) {
      return;
    }
    this.brush = this.project.workMode === 'REMOTE' ? 'REMOTE' : 'ONSITE';
    const start = this.initialMonth();
    this.year = start.year;
    this.month = start.month;
    this.loadSavedReports();
    this.loadMonth();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get editable(): boolean {
    return this.report?.status === 'DRAFT' || this.report?.status === 'REJECTED';
  }

  get monthLabel(): string {
    return this.formatMonth(this.year, this.month);
  }

  /** Totals of the days on screen, saved or not. */
  get liveTotals(): ReturnType<typeof totals> {
    return totals(this.days);
  }

  get liveAmount(): number | undefined {
    return this.report?.dailyRate ? this.liveTotals.worked * this.report.dailyRate : undefined;
  }

  /** The client whose template prints the PDF: the ESN when the mission has one. */
  get templateOwner(): string | undefined {
    return this.project.middlemanName || this.project.clientName;
  }

  get mailtoHref(): string {
    const params = {
      month: this.monthLabel,
      role: this.project.role,
      days: formatNumber(this.liveTotals.worked, this.locale, '1.0-1'),
      link: this.link
    };
    const subject = this.translate.instant('activityReports.link.emailSubject', params);
    const body = this.translate.instant('activityReports.link.emailBody', params);
    return `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
  }

  formatMonth(year: number, month: number): string {
    const label = formatDate(new Date(year, month - 1, 1), 'MMMM y', this.locale);
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  formatDay(isoDateTime?: string): string {
    return isoDateTime ? formatDate(isoDateTime, 'mediumDate', this.locale) : '';
  }

  changeMonth(delta: number): void {
    const target = new Date(this.year, this.month - 1 + delta, 1);
    this.openMonth(target.getFullYear(), target.getMonth() + 1);
  }

  openMonth(year: number, month: number): void {
    if (year === this.year && month === this.month) {
      return;
    }
    this.confirmDiscard().subscribe(confirmed => {
      if (confirmed) {
        this.year = year;
        this.month = month;
        this.loadMonth();
      }
    });
  }

  onDayToggled(date: string): void {
    this.days = applyBrush(this.days, date, this.brush);
    this.dirty = true;
  }

  fill(): void {
    if (!this.report) {
      return;
    }
    const type = this.project.workMode === 'REMOTE' ? 'REMOTE' : 'ONSITE';
    this.days = fillBusinessDays(this.days, this.year, this.month, this.report.publicHolidays, type,
      this.report.missionStart, this.report.missionEnd);
    this.dirty = true;
  }

  clearAll(): void {
    this.days = [];
    this.dirty = true;
  }

  save(): void {
    this.isBusy = true;
    this.saveChanges().pipe(takeUntil(this.destroy$)).subscribe({
      next: () => {
        this.isBusy = false;
        this.notifications.success('activityReports.saved', 2500);
      },
      error: () => this.fail('activityReports.saveError')
    });
  }

  /** Saves pending changes, then locks the month and shows its validation link. */
  submit(): void {
    this.isBusy = true;
    this.saveChanges()
      .pipe(
        switchMap(() => this.reports.submit(this.project.id!, this.year, this.month)),
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: link => {
          this.showLink(link);
          this.notifications.success('activityReports.submitted', 4000);
        },
        error: () => this.fail('activityReports.submitError')
      });
  }

  renewLink(): void {
    this.isBusy = true;
    this.reports.renewLink(this.project.id!, this.year, this.month).pipe(takeUntil(this.destroy$)).subscribe({
      next: link => this.showLink(link),
      error: () => this.fail('activityReports.link.renewError')
    });
  }

  copyLink(): void {
    if (this.link && this.clipboard.copy(this.link)) {
      this.notifications.success('activityReports.link.copied');
    } else {
      this.notifications.error('activityReports.link.copyFailed');
    }
  }

  reopen(): void {
    const report = this.report;
    if (!report) {
      return;
    }
    const validated = report.status === 'VALIDATED';
    this.confirmDialog.confirm({
      messageKey: validated ? 'activityReports.reopenValidatedConfirm' : 'activityReports.reopenConfirm',
      messageParams: { name: report.validatorName },
      danger: validated
    }).pipe(takeUntil(this.destroy$)).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.isBusy = true;
      this.reports.reopen(this.project.id!, this.year, this.month).pipe(takeUntil(this.destroy$)).subscribe({
        next: reopened => {
          this.applyReport(reopened);
          this.isBusy = false;
          this.loadSavedReports();
          this.notifications.success('activityReports.reopened', 2500);
        },
        error: () => this.fail('activityReports.reopenError')
      });
    });
  }

  downloadPdf(): void {
    const month = String(this.month).padStart(2, '0');
    this.reports.downloadPdf(this.project.id!, this.year, this.month).pipe(takeUntil(this.destroy$)).subscribe({
      next: blob => saveBlob(blob, `CRA-${this.year}-${month}.pdf`),
      error: () => this.notifications.error('activityReports.pdfError')
    });
  }

  private loadMonth(): void {
    this.isLoading = true;
    this.link = undefined;
    this.reports.getReport(this.project.id!, this.year, this.month).pipe(takeUntil(this.destroy$)).subscribe({
      next: report => {
        this.applyReport(report);
        this.isLoading = false;
      },
      error: () => {
        this.report = undefined;
        this.isLoading = false;
        this.notifications.error('activityReports.loadError');
      }
    });
  }

  private loadSavedReports(): void {
    this.reports.getReports(this.project.id!).pipe(takeUntil(this.destroy$)).subscribe({
      next: reports => this.savedReports = reports,
      error: () => this.savedReports = []
    });
  }

  /** Saves the month when it has unsaved changes, answering the saved report. */
  private saveChanges(): Observable<ActivityReport> {
    if (!this.dirty && this.report?.id) {
      return of(this.report);
    }
    const update = { comment: this.comment.trim() || undefined, days: this.days };
    return this.reports.save(this.project.id!, this.year, this.month, update).pipe(
      switchMap(saved => {
        this.applyReport(saved);
        this.loadSavedReports();
        return of(saved);
      })
    );
  }

  private showLink(link: ActivityReportLink): void {
    this.applyReport(link.report);
    const path = this.location.prepareExternalUrl('/activity-report');
    this.link = `${new URL(path, this.document.baseURI).href}#${link.token}`;
    this.linkExpiresAt = link.expiresAt;
    this.isBusy = false;
    this.loadSavedReports();
  }

  private applyReport(report: ActivityReport): void {
    this.report = report;
    this.days = [...report.days];
    this.comment = report.comment ?? '';
    this.dirty = false;
  }

  private fail(messageKey: string): void {
    this.isBusy = false;
    this.notifications.error(messageKey);
  }

  private confirmDiscard(): Observable<boolean> {
    return this.dirty
      ? this.confirmDialog.confirm({ messageKey: 'activityReports.discardConfirm', danger: true })
      : of(true);
  }

  /** The current month, kept within the mission when its dates are known. */
  private initialMonth(): { year: number; month: number } {
    const today = new Date();
    let current = today.getFullYear() * 12 + today.getMonth();
    const start = monthIndex(this.project.startDate);
    if (start !== undefined && current < start) {
      current = start;
    }
    const end = start !== undefined && this.project.durationInMonths
      ? start + this.project.durationInMonths - 1
      : undefined;
    if (end !== undefined && current > end) {
      current = end;
    }
    return { year: Math.floor(current / 12), month: (current % 12) + 1 };
  }
}

/** Months since year 0 of an ISO date, to compare months. */
function monthIndex(isoDate?: string): number | undefined {
  if (!isoDate) {
    return undefined;
  }
  const [year, month] = isoDate.split('-').map(Number);
  return year * 12 + month - 1;
}
