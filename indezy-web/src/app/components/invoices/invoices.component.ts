import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, forkJoin } from 'rxjs';

import { BillingIdentity, ClientDto, Invoice, InvoiceDraft, InvoiceableReport, ProjectDto } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { BillingService } from '../../services/billing/billing.service';
import { ClientService } from '../../services/client/client.service';
import { AccountingExportFormat, InvoiceService } from '../../services/invoice/invoice.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../shared/utils/download-file';
import { invoiceErrorKey } from './invoice-errors';
import { InvoiceDateDialogComponent, InvoiceDateDialogData } from './date-dialog/invoice-date-dialog.component';
import { InvoiceDraftDialogComponent, InvoiceDraftDialogData } from './draft-dialog/invoice-draft-dialog.component';
import { InvoiceReportDialogComponent } from './report-dialog/invoice-report-dialog.component';

export type InvoiceFilter = 'ALL' | 'DRAFT' | 'ISSUED' | 'OVERDUE' | 'PAID';

/** The workspace's invoices: drafts to write and issue, issued ones to settle, and what is still owed. */
@Component({
  selector: 'app-invoices',
  imports: [DatePipe, DecimalPipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatCardModule, MatIconModule, MatMenuModule,
    MatProgressSpinnerModule, TranslateModule],
  templateUrl: './invoices.component.html',
  styleUrls: ['./invoices.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class InvoicesComponent implements OnInit {
  private readonly invoiceService = inject(InvoiceService);
  private readonly billingService = inject(BillingService);
  private readonly clientService = inject(ClientService);
  private readonly projectService = inject(ProjectService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);

  readonly filters: InvoiceFilter[] = ['ALL', 'DRAFT', 'ISSUED', 'OVERDUE', 'PAID'];
  filter: InvoiceFilter = 'ALL';
  invoices: Invoice[] = [];
  billing: BillingIdentity | null = null;
  /** The months of activity reports not invoiced yet, counted on the button that invoices them. */
  toInvoice: InvoiceableReport[] = [];
  loading = true;

  get shown(): Invoice[] {
    return this.invoices.filter(invoice => this.filter === 'ALL' || (this.filter === 'OVERDUE' ? invoice.overdue :
      this.filter === 'ISSUED' ? invoice.status === 'ISSUED' && !invoice.overdue : invoice.status === this.filter));
  }

  /** The years with an issued invoice or credit note, the latest first: what the accountant's export can hold. */
  get exportYears(): number[] {
    const years = this.invoices.filter(invoice => invoice.number && invoice.issueDate)
      .map(invoice => Number(invoice.issueDate!.slice(0, 4)));
    return [...new Set(years)].sort((a, b) => b - a);
  }

  /** Issued invoices not paid yet, less what credit notes took back from them. */
  get outstanding(): number {
    return this.due(this.invoices.filter(invoice => invoice.kind !== 'CREDIT_NOTE' && invoice.status === 'ISSUED'));
  }

  get overdue(): number {
    return this.due(this.invoices.filter(invoice => invoice.overdue));
  }

  ngOnInit(): void {
    const freelanceId = this.auth.getUser()?.id;
    if (!freelanceId) {
      this.loading = false;
      return;
    }
    forkJoin({ invoices: this.invoiceService.list(freelanceId), billing: this.billingService.identity() }).subscribe({
      next: ({ invoices, billing }) => {
        this.invoices = invoices;
        this.billing = billing;
        this.loading = false;
        this.loadInvoiceable();
      },
      error: () => {
        this.loading = false;
        this.notifications.error('invoices.loadError');
      }
    });
  }

  /** Writes a new draft, or changes one, among the workspace's clients and missions. */
  write(invoice?: Invoice): void {
    forkJoin({ clients: this.clientService.getForCurrentFreelance(), projects: this.projects() }).subscribe({
      next: ({ clients, projects }) => {
        const data: InvoiceDraftDialogData = { clients: this.sorted(clients), projects, invoice,
          vatRate: this.billing?.vatExempt === false ? this.billing.vatRate ?? 20 : 0 };
        this.dialog.open(InvoiceDraftDialogComponent, { data, width: '760px', maxWidth: '96vw', autoFocus: false })
          .afterClosed().subscribe((draft?: InvoiceDraft) => {
            if (draft) {
              this.saveDraft(invoice, draft);
            }
          });
      },
      error: () => this.notifications.error('invoices.loadError')
    });
  }

  /** Lists the months of activity reports to invoice and drafts the one picked: its days worked at the mission's daily rate. */
  fromReport(): void {
    this.invoiceService.invoiceableReports().subscribe({
      next: reports => {
        this.toInvoice = reports;
        this.dialog.open(InvoiceReportDialogComponent, { data: reports, width: '640px', maxWidth: '96vw', autoFocus: false })
          .afterClosed().subscribe((report?: InvoiceableReport) => {
            if (report) {
              this.apply(this.invoiceService.fromReport(report.projectId, report.year, report.month), 'invoices.fromReportCreated',
                () => this.toInvoice = this.toInvoice.filter(item => item.reportId !== report.reportId));
            }
          });
      },
      error: () => this.notifications.error('invoices.loadError')
    });
  }

  issue(invoice: Invoice): void {
    const today = new Date().toLocaleDateString('sv');
    const data: InvoiceDateDialogData = { titleKey: 'invoices.issueTitle', messageKey: 'invoices.issueMessage', labelKey: 'invoices.issueDate',
      confirmKey: 'invoices.issue', date: today, max: today };
    this.dialog.open(InvoiceDateDialogComponent, { data, width: '420px' }).afterClosed().subscribe((date?: string) => {
      if (date) {
        this.apply(this.invoiceService.issue(invoice.id, date), 'invoices.issued');
      }
    });
  }

  markPaid(invoice: Invoice): void {
    const today = new Date().toLocaleDateString('sv');
    const data: InvoiceDateDialogData = { titleKey: 'invoices.paidTitle', messageKey: 'invoices.paidMessage',
      messageParams: { number: invoice.number }, labelKey: 'invoices.paidOn', confirmKey: 'invoices.markPaid', date: today, max: today };
    this.dialog.open(InvoiceDateDialogComponent, { data, width: '420px' }).afterClosed().subscribe((date?: string) => {
      if (date) {
        this.apply(this.invoiceService.setPaid(invoice.id, date), 'invoices.paidDone');
      }
    });
  }

  /** Drafts a credit note of an issued invoice, then opens it to reduce for a partial credit. */
  creditNote(invoice: Invoice): void {
    this.invoiceService.creditNote(invoice.id).subscribe({
      next: note => {
        this.invoices = [note, ...this.invoices];
        this.notifications.success('invoices.creditNoteCreated');
        this.write(note);
      },
      error: (error: HttpErrorResponse) => this.notifications.error(invoiceErrorKey(error, 'invoices.saveError'))
    });
  }

  markUnpaid(invoice: Invoice): void {
    this.apply(this.invoiceService.setPaid(invoice.id, null), 'invoices.unpaidDone');
  }

  remove(invoice: Invoice): void {
    this.confirm.confirm({ messageKey: 'invoices.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.invoiceService.delete(invoice.id).subscribe({
        next: () => {
          this.invoices = this.invoices.filter(item => item.id !== invoice.id);
          this.notifications.success('invoices.deleted');
          if (invoice.activityReportId) {
            this.loadInvoiceable();
          }
        },
        error: () => this.notifications.error('invoices.saveError')
      });
    });
  }

  download(invoice: Invoice): void {
    this.invoiceService.downloadPdf(invoice.id).subscribe({
      next: blob => saveBlob(blob, `${this.fileName(invoice)}-${invoice.number ?? 'draft-' + invoice.id}.pdf`),
      error: () => this.notifications.error('invoices.pdfError')
    });
  }

  exportYear(year: number, format: AccountingExportFormat): void {
    const english = this.translate.getCurrentLang() === 'en';
    this.invoiceService.exportYear(year, format, english ? 'EN' : 'FR').subscribe({
      next: blob => saveBlob(blob, format === 'FEC' ? `${this.billing?.siret?.slice(0, 9) ?? ''}FEC${year}1231.txt` :
        `${english ? 'invoices' : 'factures'}-${year}.csv`),
      error: () => this.notifications.error('invoices.exportError')
    });
  }

  private fileName(invoice: Invoice): string {
    const english = invoice.language === 'EN';
    return invoice.kind === 'CREDIT_NOTE' ? (english ? 'credit-note' : 'avoir') : (english ? 'invoice' : 'facture');
  }

  private saveDraft(invoice: Invoice | undefined, draft: InvoiceDraft): void {
    const request = invoice ? this.invoiceService.update(invoice.id, draft) : this.invoiceService.create(draft);
    this.apply(request, invoice ? 'invoices.saved' : 'invoices.created');
  }

  /** A deleted draft of a report frees the month, so the count is read again; it stays as it was when it cannot be. */
  private loadInvoiceable(): void {
    this.invoiceService.invoiceableReports().subscribe({ next: reports => this.toInvoice = reports, error: () => undefined });
  }

  /** Shows the invoice as the server answered, first in the list when new; a refusal says why when the server does. */
  private apply(request: Observable<Invoice>, successKey: string, saved?: () => void): void {
    request.subscribe({
      next: invoice => {
        const known = this.invoices.some(item => item.id === invoice.id);
        this.invoices = known ? this.invoices.map(item => item.id === invoice.id ? invoice : item) : [invoice, ...this.invoices];
        this.notifications.success(successKey);
        saved?.();
      },
      error: (error: HttpErrorResponse) => this.notifications.error(invoiceErrorKey(error, 'invoices.saveError'))
    });
  }

  private projects(): Observable<ProjectDto[]> {
    return this.projectService.getByFreelanceId(this.auth.getUser()!.id);
  }

  private sorted(clients: ClientDto[]): ClientDto[] {
    return [...clients].sort((a, b) => a.companyName.localeCompare(b.companyName));
  }

  /** What the invoices total, less their issued credit notes. */
  private due(owed: Invoice[]): number {
    const owedIds = new Set(owed.map(invoice => invoice.id));
    const credited = this.invoices.filter(note => note.kind === 'CREDIT_NOTE' && note.status === 'ISSUED' &&
      owedIds.has(note.creditedInvoiceId!)).reduce((sum, note) => sum + note.total, 0);
    return owed.reduce((sum, invoice) => sum + invoice.total, 0) - credited;
  }
}
