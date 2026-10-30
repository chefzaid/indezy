import { ChangeDetectionStrategy, Component, LOCALE_ID, OnDestroy, OnInit, inject } from '@angular/core';
import { DecimalPipe, formatDate } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Subject, takeUntil } from 'rxjs';

import { PublicActivityReport } from '../../models';
import { ActivityReportService } from '../../services/activity-report/activity-report.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ActivityCalendarComponent } from '../../shared/components/activity-calendar/activity-calendar.component';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';
import { saveBlob } from '../../shared/utils/download-file';

/**
 * The page a client or ESN opens from an activity report's validation link, without an account: the month as the
 * consultant recorded it, the PDF, and the approval or rejection signed with the validator's name. The link token
 * comes after the # of the link, so it never reaches a server log.
 */
@Component({
  selector: 'app-activity-report-validation',
  imports: [
    DecimalPipe,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule,
    ActivityCalendarComponent,
    PublicPageHeaderComponent
  ],
  templateUrl: './activity-report-validation.component.html',
  styleUrls: ['./activity-report-validation.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ActivityReportValidationComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly reports = inject(ActivityReportService);
  private readonly notifications = inject(NotificationService);
  private readonly locale = inject(LOCALE_ID);
  private readonly destroy$ = new Subject<void>();

  readonly form = inject(FormBuilder).nonNullable.group({
    validatorName: ['', [Validators.required, Validators.maxLength(120)]],
    comment: ['', [Validators.maxLength(1000)]]
  });

  view?: PublicActivityReport;
  isLoading = true;
  invalidLink = false;
  isSending = false;
  /** Set once the validator answered on this page. */
  answered = false;
  private token = '';

  ngOnInit(): void {
    this.token = (this.route.snapshot.fragment ?? '').trim();
    if (!this.token) {
      this.isLoading = false;
      this.invalidLink = true;
      return;
    }
    this.load();
  }

  private load(): void {
    this.isLoading = true;
    this.reports.getPublicReport(this.token).pipe(takeUntil(this.destroy$)).subscribe({
      next: view => {
        this.view = view;
        this.isLoading = false;
      },
      error: () => {
        this.isLoading = false;
        this.invalidLink = true;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get monthLabel(): string {
    if (!this.view) {
      return '';
    }
    const label = formatDate(new Date(this.view.report.year, this.view.report.month - 1, 1), 'MMMM y', this.locale);
    return label.charAt(0).toUpperCase() + label.slice(1);
  }

  get decidedAt(): string {
    const decided = this.view?.report.decidedAt;
    return decided ? `${formatDate(decided, 'mediumDate', this.locale)} ${formatDate(decided, 'shortTime', this.locale)}` : '';
  }

  approve(): void {
    this.decide(true);
  }

  /** A rejection needs a reason, asked in the comment field. */
  reject(): void {
    const comment = this.form.controls.comment;
    if (!comment.value.trim()) {
      comment.setErrors({ reasonRequired: true });
      comment.markAsTouched();
      return;
    }
    this.decide(false);
  }

  downloadPdf(): void {
    const month = String(this.view?.report.month ?? '').padStart(2, '0');
    this.reports.downloadPublicPdf(this.token).pipe(takeUntil(this.destroy$)).subscribe({
      next: blob => saveBlob(blob, `CRA-${this.view?.report.year}-${month}.pdf`),
      error: () => this.notifications.error('activityReports.pdfError')
    });
  }

  private decide(approved: boolean): void {
    const name = this.form.controls.validatorName;
    if (name.invalid || !name.value.trim()) {
      name.setErrors({ required: true });
      name.markAsTouched();
      return;
    }
    this.isSending = true;
    const { validatorName, comment } = this.form.getRawValue();
    this.reports.decide(this.token, { approved, validatorName: validatorName.trim(), comment: comment.trim() || undefined })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: view => {
          this.view = view;
          this.answered = true;
          this.isSending = false;
        },
        error: (error: HttpErrorResponse) => {
          this.isSending = false;
          if (error.status === 409) {
            this.notifications.error('activityReports.validation.alreadyDecided', 5000);
            this.load();
          } else {
            this.notifications.error('activityReports.validation.decisionError', 5000);
          }
        }
      });
  }
}
