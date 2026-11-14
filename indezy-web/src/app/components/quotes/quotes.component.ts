import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, forkJoin } from 'rxjs';

import { BillingIdentity, ClientDto, Invoice, Quote, QuoteDraft } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { BillingService } from '../../services/billing/billing.service';
import { ClientService } from '../../services/client/client.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { QuoteService } from '../../services/quote/quote.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../shared/utils/download-file';
import { InvoiceDateDialogComponent, InvoiceDateDialogData } from '../invoices/date-dialog/invoice-date-dialog.component';
import { InvoiceDraftDialogComponent, InvoiceDraftDialogData } from '../invoices/draft-dialog/invoice-draft-dialog.component';
import { quoteErrorKey } from './quote-errors';

export type QuoteFilter = 'ALL' | 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REFUSED';

/** The workspace's quotes: drafts to write and send, sent ones the client accepts or refuses, accepted ones to invoice. */
@Component({
  selector: 'app-quotes',
  imports: [PageHeaderComponent, DatePipe, DecimalPipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatCardModule, MatIconModule,
    MatMenuModule, MatProgressSpinnerModule, TranslateModule],
  templateUrl: './quotes.component.html',
  styleUrls: ['./quotes.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class QuotesComponent implements OnInit {
  private readonly quoteService = inject(QuoteService);
  private readonly billingService = inject(BillingService);
  private readonly clientService = inject(ClientService);
  private readonly projectService = inject(ProjectService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);
  private readonly router = inject(Router);

  readonly filters: QuoteFilter[] = ['ALL', 'DRAFT', 'SENT', 'ACCEPTED', 'REFUSED'];
  filter: QuoteFilter = 'ALL';
  quotes: Quote[] = [];
  billing: BillingIdentity | null = null;
  loading = true;

  get shown(): Quote[] {
    return this.quotes.filter(quote => this.filter === 'ALL' || quote.status === this.filter);
  }

  /** Sent quotes still waiting for the client's answer, before VAT: the work in the balance. */
  get pending(): number {
    return this.quotes.filter(quote => quote.status === 'SENT' && !quote.expired).reduce((sum, quote) => sum + quote.totalExcludingVat, 0);
  }

  /** The share of answered quotes the clients accepted, in percent; null before any answer. */
  get acceptanceRate(): number | null {
    const answered = this.quotes.filter(quote => quote.status === 'ACCEPTED' || quote.status === 'REFUSED');
    return answered.length === 0 ? null : Math.round(100 * answered.filter(quote => quote.status === 'ACCEPTED').length / answered.length);
  }

  ngOnInit(): void {
    forkJoin({ quotes: this.quoteService.list(), billing: this.billingService.identity() }).subscribe({
      next: ({ quotes, billing }) => {
        this.quotes = quotes;
        this.billing = billing;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.notifications.error('quotes.loadError');
      }
    });
  }

  /** Writes a new draft, or changes one, among the workspace's clients and missions. */
  write(quote?: Quote): void {
    const freelanceId = this.auth.getUser()?.id;
    if (!freelanceId) {
      return;
    }
    forkJoin({ clients: this.clientService.getForCurrentFreelance(), projects: this.projectService.getByFreelanceId(freelanceId) }).subscribe({
      next: ({ clients, projects }) => {
        const data: InvoiceDraftDialogData = { clients: this.sorted(clients), projects, quote, forQuote: true,
          vatRate: this.billing?.vatExempt === false ? this.billing.vatRate ?? 20 : 0 };
        this.dialog.open(InvoiceDraftDialogComponent, { data, width: '760px', maxWidth: '96vw', autoFocus: false })
          .afterClosed().subscribe((draft?: QuoteDraft) => {
            if (draft) {
              this.apply(quote ? this.quoteService.update(quote.id, draft) : this.quoteService.create(draft), quote ? 'quotes.saved' : 'quotes.created');
            }
          });
      },
      error: () => this.notifications.error('quotes.loadError')
    });
  }

  send(quote: Quote): void {
    const today = new Date().toLocaleDateString('sv');
    const data: InvoiceDateDialogData = { titleKey: 'quotes.sendTitle', messageKey: 'quotes.sendMessage', labelKey: 'quotes.sendDate',
      confirmKey: 'quotes.send', date: today, max: today };
    this.dialog.open(InvoiceDateDialogComponent, { data, width: '420px' }).afterClosed().subscribe((date?: string) => {
      if (date) {
        this.apply(this.quoteService.send(quote.id, date), 'quotes.sent');
      }
    });
  }

  decide(quote: Quote, accepted: boolean): void {
    this.apply(this.quoteService.decide(quote.id, accepted), accepted ? 'quotes.acceptedDone' : 'quotes.refusedDone');
  }

  /** Drafts the final invoice of an accepted quote, less its issued deposit, then opens the invoices to review and issue it. */
  invoice(quote: Quote): void {
    this.bill(this.quoteService.invoice(quote.id), invoice => ({ ...quote, invoiceId: invoice.id }), 'quotes.invoiced');
  }

  /** Drafts the deposit invoice of an accepted quote, then opens the invoices to issue it. */
  deposit(quote: Quote): void {
    this.bill(this.quoteService.deposit(quote.id), invoice => ({ ...quote, depositInvoiceId: invoice.id }), 'quotes.depositInvoiced');
  }

  private bill(request: Observable<Invoice>, billed: (invoice: Invoice) => Quote, successKey: string): void {
    request.subscribe({
      next: invoice => {
        const changed = billed(invoice);
        this.quotes = this.quotes.map(item => item.id === changed.id ? changed : item);
        this.notifications.success(successKey);
        this.router.navigate(['/invoices']);
      },
      error: (error: HttpErrorResponse) => this.notifications.error(quoteErrorKey(error, 'quotes.saveError'))
    });
  }

  remove(quote: Quote): void {
    this.confirm.confirm({ messageKey: 'quotes.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.quoteService.delete(quote.id).subscribe({
        next: () => {
          this.quotes = this.quotes.filter(item => item.id !== quote.id);
          this.notifications.success('quotes.deleted');
        },
        error: () => this.notifications.error('quotes.saveError')
      });
    });
  }

  download(quote: Quote): void {
    const english = quote.language === 'EN';
    this.quoteService.downloadPdf(quote.id).subscribe({
      next: blob => saveBlob(blob, `${english ? 'quote' : 'devis'}-${quote.number ?? (english ? 'draft-' : 'brouillon-') + quote.id}.pdf`),
      error: () => this.notifications.error('quotes.pdfError')
    });
  }

  /** Shows the quote as the server answered, first in the list when new; a refusal says why when the server does. */
  private apply(request: Observable<Quote>, successKey: string): void {
    request.subscribe({
      next: quote => {
        const known = this.quotes.some(item => item.id === quote.id);
        this.quotes = known ? this.quotes.map(item => item.id === quote.id ? quote : item) : [quote, ...this.quotes];
        this.notifications.success(successKey);
      },
      error: (error: HttpErrorResponse) => this.notifications.error(quoteErrorKey(error, 'quotes.saveError'))
    });
  }

  private sorted(clients: ClientDto[]): ClientDto[] {
    return [...clients].sort((a, b) => a.companyName.localeCompare(b.companyName));
  }
}
