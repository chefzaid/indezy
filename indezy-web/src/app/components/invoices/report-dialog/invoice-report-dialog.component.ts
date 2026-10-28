import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';

import { InvoiceableReport } from '../../../models';

/**
 * The months of activity reports to invoice, each with its days worked times the mission's daily rate; closes with the
 * one picked, or with nothing. A month without a daily rate or a client to bill says what it misses.
 */
@Component({
  selector: 'app-invoice-report-dialog',
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatDialogModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'invoices.fromReportTitle' | translate }}</h2>
    <mat-dialog-content>
      @if (reports.length === 0) {
        <p data-no-invoiceable-report>{{ 'invoices.fromReportNone' | translate }}</p>
      } @else {
        <p class="intro">{{ 'invoices.fromReportIntro' | translate }}</p>
        <ul class="reports">
          @for (report of reports; track report.reportId) {
            <li class="report" [attr.data-invoiceable-report]="report.reportId">
              <span class="what">
                <span class="mission">{{ report.projectRole }}@if (report.clientName) { · {{ report.clientName }} }</span>
                <span class="month">{{ monthOf(report) | date:'MMMM y' }} · {{ 'activityReports.statuses.' + report.status | translate }}</span>
                @if (report.amount === null) {
                  <span class="missing">{{ 'invoices.fromReportNoRate' | translate }}</span>
                } @else if (!report.clientName) {
                  <span class="missing">{{ 'invoices.fromReportNoClient' | translate }}</span>
                } @else {
                  <span class="amount">{{ 'invoices.fromReportAmount' | translate: { days: (report.workedDays | number:'1.0-2'),
                    rate: (report.dailyRate | number:'1.0-2'), amount: (report.amount | number:'1.2-2') } }}</span>
                }
              </span>
              <button mat-stroked-button color="primary" type="button" [disabled]="report.amount === null || !report.clientName"
                [mat-dialog-close]="report" data-invoice-this-report>{{ 'invoices.fromReportCreate' | translate }}</button>
            </li>
          }
        </ul>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="undefined">{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .intro { color: var(--app-text-muted); }
    .reports { list-style: none; margin: 0; padding: 0; }
    .report { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 0;
      border-bottom: 1px solid var(--app-border, rgba(0, 0, 0, 0.12)); }
    .report:last-child { border-bottom: none; }
    .what { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
    .mission { font-weight: 500; }
    .month::first-letter { text-transform: uppercase; }
    .month, .missing { color: var(--app-text-muted); font-size: 0.875rem; }
    .amount { font-variant-numeric: tabular-nums; }
    @media (max-width: 600px) { .report { flex-direction: column; align-items: stretch; } }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class InvoiceReportDialogComponent {
  readonly reports = inject<InvoiceableReport[]>(MAT_DIALOG_DATA);

  monthOf(report: InvoiceableReport): Date {
    return new Date(report.year, report.month - 1, 1);
  }
}
