import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { BillingIdentity, ClientDto, Invoice, ProjectDto, Quote, QuoteDraft } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { BillingService } from '../../services/billing/billing.service';
import { ClientService } from '../../services/client/client.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { QuoteService } from '../../services/quote/quote.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { ClientEmailDialogComponent } from '../../shared/components/client-email-dialog/client-email-dialog.component';
import { InvoiceDraftDialogData } from '../invoices/draft-dialog/invoice-draft-dialog.component';
import { quoteErrorKey } from './quote-errors';
import { QuotesComponent } from './quotes.component';
import en from '../../../assets/i18n/en.json';

describe('QuotesComponent', () => {
  let quoteService: jasmine.SpyObj<QuoteService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let user: { id: number } | null;
  const quote = (id: number, changes: Partial<Quote> = {}): Quote => ({
    id, number: null, status: 'DRAFT', expired: false, clientId: 3, clientName: 'Acme', projectId: 8, projectRole: 'Audit',
    issueDate: null, validityDays: 30, validUntil: null, depositPercent: null, language: 'FR', notes: null, lines: [],
    totalExcludingVat: 4000, vat: 0, total: 4000, decidedOn: null, invoiceId: null,
    depositInvoiceId: null, createdAt: '2026-11-14T10:00:00', ...changes
  });
  const draft = quote(1, { depositPercent: 30 });
  const sent = quote(2, { number: 'D2026-0001', status: 'SENT', issueDate: '2026-11-01', validUntil: '2026-12-01', totalExcludingVat: 2000 });
  const expired = quote(3, { number: 'D2026-0002', status: 'SENT', expired: true, validUntil: '2026-10-01' });
  const accepted = quote(4, { number: 'D2026-0003', status: 'ACCEPTED', decidedOn: '2026-11-10', language: 'EN' });
  const refused = quote(5, { number: 'D2026-0004', status: 'REFUSED', decidedOn: '2026-11-11', invoiceId: null });
  const billing = { complete: false, vatExempt: false, vatRate: 20 } as BillingIdentity;
  const closing = (result: unknown) => dialog.open.and.returnValue({ afterClosed: () => of(result) } as MatDialogRef<unknown>);
  const refusal = (message: string) => new HttpErrorResponse({ status: 409, error: { message } });

  function create(list = of([draft, sent, expired, accepted, refused])): ComponentFixture<QuotesComponent> {
    quoteService = jasmine.createSpyObj('QuoteService', ['list', 'create', 'update', 'delete', 'send', 'decide', 'deposit', 'invoice',
      'downloadPdf', 'downloadDocx']);
    quoteService.list.and.returnValue(list);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [QuotesComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]), { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
        { provide: QuoteService, useValue: quoteService },
        { provide: BillingService, useValue: { identity: () => of(billing) } },
        { provide: ClientService, useValue: { getForCurrentFreelance: () => of([{ id: 4, companyName: 'Zeta' }, { id: 3, companyName: 'Acme' }] as ClientDto[]) } },
        { provide: ProjectService, useValue: { getByFreelanceId: () => of([{ id: 8 }] as ProjectDto[]) } },
        { provide: AuthService, useValue: { getUser: () => user } },
        { provide: MatDialog, useValue: dialog },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(QuotesComponent);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    user = { id: 7 };
  });

  it('lists the quotes with what awaits an answer and how many were accepted, filtered by status', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;

    expect(component.pending).toBe(2000);
    expect(component.acceptanceRate).toBe(50);
    expect(element.querySelector('[data-quotes-acceptance]')?.textContent).toContain('50 %');
    expect(element.querySelector('[data-billing-incomplete]')).not.toBeNull();
    expect(element.querySelector('[data-quote="1"]')?.textContent).toContain('30 % deposit');
    expect(element.querySelector('[data-quote="1"]')?.textContent).toContain('valid 30 days once sent');
    expect(element.querySelector('[data-quote="2"]')?.textContent).toContain('valid until');
    expect(element.querySelector('[data-quote="3"]')?.textContent).toContain('Expired');
    expect(element.querySelector('[data-quote="4"]')?.textContent).toContain('accepted');
    expect(element.querySelector('[data-quote="5"]')?.textContent).toContain('refused');
    component.filter = 'SENT';
    expect(component.shown.map(item => item.id)).toEqual([2, 3]);
    component.filter = 'ACCEPTED';
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-quote]').length).toBe(1);
  });

  it('follows up a sent quote, for one of its client\'s contacts', () => {
    const component = create().componentInstance;
    const sent = component.quotes.find(item => item.id === 2)!;
    component.followUp(sent);
    expect(dialog.open).toHaveBeenCalledWith(ClientEmailDialogComponent, jasmine.objectContaining({ data: jasmine.objectContaining({
      clientId: sent.clientId, title: `Follow-up of ${sent.number}` }) }));
  });

  it('says when there is no quote, none answered yet, or the quotes cannot be loaded', () => {
    let fixture = create(of([]));
    expect(fixture.componentInstance.acceptanceRate).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No quote yet');
    TestBed.resetTestingModule();
    fixture = create(throwError(() => new Error('down')));
    expect(fixture.componentInstance.loading).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('quotes.loadError');
  });

  it('writes a new quote and changes a draft among the clients, sorted by name', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const written: QuoteDraft = { clientId: 3, language: 'FR', validityDays: 30, lines: [] };
    quoteService.create.and.returnValue(of(quote(9)));
    closing(written);
    component.write();
    const data = dialog.open.calls.mostRecent().args[1]!.data as InvoiceDraftDialogData;
    expect(data.forQuote).toBeTrue();
    expect(data.vatRate).toBe(20);
    expect(data.clients.map(client => client.companyName)).toEqual(['Acme', 'Zeta']);
    expect(quoteService.create).toHaveBeenCalledWith(written);
    expect(component.quotes[0].id).toBe(9);
    expect(notifications.success).toHaveBeenCalledWith('quotes.created');

    quoteService.update.and.returnValue(of({ ...draft, notes: 'Changed' }));
    component.write(draft);
    expect(quoteService.update).toHaveBeenCalledWith(1, written);
    expect(component.quotes.find(item => item.id === 1)?.notes).toBe('Changed');

    closing(undefined);
    component.write();
    expect(quoteService.create).toHaveBeenCalledTimes(1);
    user = null;
    component.write();
    expect(dialog.open).toHaveBeenCalledTimes(3);
  });

  it('sends a draft on the day picked, saying why the server refuses', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    quoteService.send.and.returnValue(of({ ...draft, number: 'D2026-0005', status: 'SENT' }));
    closing('2026-11-14');
    component.send(draft);
    expect(quoteService.send).toHaveBeenCalledWith(1, '2026-11-14');
    expect(component.quotes.find(item => item.id === 1)?.status).toBe('SENT');

    quoteService.send.and.returnValue(throwError(() => refusal('Complete the billing identity before sending a quote')));
    component.send(draft);
    expect(notifications.error).toHaveBeenCalledWith('quotes.errors.billing');
    closing(undefined);
    component.send(draft);
    expect(quoteService.send).toHaveBeenCalledTimes(2);
  });

  it('records the client\'s answer, then drafts the invoice and opens the invoices', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const router = TestBed.inject(Router);
    spyOn(router, 'navigate').and.resolveTo(true);
    quoteService.decide.and.returnValue(of({ ...sent, status: 'ACCEPTED' }));
    component.decide(sent, true);
    expect(quoteService.decide).toHaveBeenCalledWith(2, true);
    expect(notifications.success).toHaveBeenCalledWith('quotes.acceptedDone');
    component.decide(sent, false);
    expect(notifications.success).toHaveBeenCalledWith('quotes.refusedDone');

    quoteService.deposit.and.returnValue(of({ id: 39, kind: 'DEPOSIT' } as Invoice));
    component.deposit(accepted);
    expect(component.quotes.find(item => item.id === 4)?.depositInvoiceId).toBe(39);
    expect(notifications.success).toHaveBeenCalledWith('quotes.depositInvoiced');
    quoteService.deposit.and.returnValue(throwError(() => refusal('The quote asks no deposit')));
    component.deposit(sent);
    expect(notifications.error).toHaveBeenCalledWith('quotes.errors.noDeposit');

    quoteService.invoice.and.returnValue(of({ id: 40 } as Invoice));
    component.invoice(accepted);
    expect(component.quotes.find(item => item.id === 4)?.invoiceId).toBe(40);
    expect(router.navigate).toHaveBeenCalledWith(['/invoices']);
    quoteService.invoice.and.returnValue(throwError(() => refusal('This quote is already invoiced')));
    component.invoice(accepted);
    expect(notifications.error).toHaveBeenCalledWith('quotes.errors.alreadyInvoiced');
  });

  it('deletes a draft once confirmed, and downloads the PDF named in the quote\'s language', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    confirm.confirm.and.returnValue(of(false));
    component.remove(draft);
    expect(quoteService.delete).not.toHaveBeenCalled();
    confirm.confirm.and.returnValue(of(true));
    quoteService.delete.and.returnValue(of(undefined));
    component.remove(draft);
    expect(component.quotes.some(item => item.id === 1)).toBeFalse();
    quoteService.delete.and.returnValue(throwError(() => new Error('down')));
    component.remove(sent);
    expect(notifications.error).toHaveBeenCalledWith('quotes.saveError');

    const link = document.createElement('a');
    spyOn(document, 'createElement').and.returnValue(link);
    spyOn(link, 'click');
    quoteService.downloadPdf.and.returnValue(of(new Blob(['%PDF'])));
    component.download(accepted);
    expect(link.download).toBe('quote-D2026-0003.pdf');
    component.download(draft);
    expect(link.download).toBe('devis-brouillon-1.pdf');
    quoteService.downloadPdf.and.returnValue(throwError(() => new Error('down')));
    component.download(sent);
    expect(notifications.error).toHaveBeenCalledWith('quotes.pdfError');
    quoteService.downloadDocx.and.returnValue(of(new Blob(['PK'])));
    component.download(accepted, true);
    expect(link.download).toBe('quote-D2026-0003.docx');
    quoteService.downloadDocx.and.returnValue(throwError(() => new Error('down')));
    component.download(sent, true);
    expect(notifications.error).toHaveBeenCalledWith('invoices.wordError');
  });

  it('tells each refusal the server explains, and falls back otherwise', () => {
    expect(quoteErrorKey(refusal('Only a sent quote can be accepted or refused'), 'x')).toBe('quotes.errors.notSent');
    expect(quoteErrorKey(refusal('Issue the deposit invoice before invoicing the balance'), 'x')).toBe('quotes.errors.depositDraft');
    expect(quoteErrorKey(refusal('Something else'), 'x')).toBe('x');
    expect(quoteErrorKey(new HttpErrorResponse({ status: 500 }), 'x')).toBe('x');
    expect(quoteErrorKey(null, 'x')).toBe('x');
  });
});
