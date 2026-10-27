import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { BillingIdentity, ClientDto, Invoice, ProjectDto } from '../../models';
import { AuthService } from '../../services/auth/auth.service';
import { BillingService } from '../../services/billing/billing.service';
import { ClientService } from '../../services/client/client.service';
import { InvoiceService } from '../../services/invoice/invoice.service';
import { NotificationService } from '../../services/notification/notification.service';
import { ProjectService } from '../../services/project/project.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { InvoicesComponent } from './invoices.component';
import en from '../../../assets/i18n/en.json';

describe('InvoicesComponent', () => {
  let invoiceService: jasmine.SpyObj<InvoiceService>;
  let dialog: jasmine.SpyObj<MatDialog>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let user: { id: number } | null;
  const invoice = (id: number, changes: Partial<Invoice> = {}): Invoice => ({
    id, number: null, status: 'DRAFT', overdue: false, clientId: 3, clientName: 'Acme', projectId: 8, projectRole: 'Java lead',
    activityReportId: null, issueDate: null, dueDate: null, periodStart: '2026-10-01', periodEnd: '2026-10-31', language: 'FR',
    notes: null, lines: [], totalExcludingVat: 1000, vat: 0, total: 1000, paidOn: null, createdAt: '2026-10-27T10:00:00', ...changes
  });
  const draft = invoice(1);
  const issued = invoice(2, { number: 'F2026-0001', status: 'ISSUED', dueDate: '2026-11-30', total: 2000 });
  const late = invoice(3, { number: 'F2026-0002', status: 'ISSUED', overdue: true, total: 500 });
  const paid = invoice(4, { number: 'F2026-0003', status: 'PAID', paidOn: '2026-10-20', language: 'EN' });
  const billing = { complete: false, vatExempt: false, vatRate: 20 } as BillingIdentity;
  const closing = (result: unknown) => dialog.open.and.returnValue({ afterClosed: () => of(result) } as MatDialogRef<unknown>);

  function create(list = of([draft, issued, late, paid])): ComponentFixture<InvoicesComponent> {
    invoiceService = jasmine.createSpyObj('InvoiceService', ['list', 'create', 'update', 'delete', 'issue', 'setPaid', 'downloadPdf']);
    invoiceService.list.and.returnValue(list);
    dialog = jasmine.createSpyObj('MatDialog', ['open']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [InvoicesComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]), provideNoopAnimations(),
        { provide: InvoiceService, useValue: invoiceService },
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
    const fixture = TestBed.createComponent(InvoicesComponent);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => user = { id: 7 });

  it('lists the invoices with what is owed and overdue, filtered by status', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const element: HTMLElement = fixture.nativeElement;

    expect(component.outstanding).toBe(2500);
    expect(component.overdue).toBe(500);
    expect(element.querySelector('[data-billing-incomplete]')).not.toBeNull();
    expect(element.querySelector('[data-invoice="1"]')?.textContent).toContain('Draft');
    expect(element.querySelector('[data-invoice="3"]')?.textContent).toContain('Overdue');
    const shown = (filter: string) => {
      component.filter = filter as never;
      return component.shown.map(item => item.id);
    };
    expect(shown('DRAFT')).toEqual([1]);
    expect(shown('ISSUED')).toEqual([2]);
    expect(shown('OVERDUE')).toEqual([3]);
    expect(shown('PAID')).toEqual([4]);
    expect(shown('ALL')).toEqual([1, 2, 3, 4]);
    fixture.detectChanges();
  });

  it('writes a new draft among the clients by name, at the workspace\'s VAT rate', () => {
    const component = create().componentInstance;
    const written = { clientId: 3, language: 'FR', lines: [] };
    closing(written);
    invoiceService.create.and.returnValue(of(invoice(9)));

    component.write();

    const data = dialog.open.calls.mostRecent().args[1]!.data as { clients: ClientDto[]; vatRate: number };
    expect(data.clients.map(client => client.companyName)).toEqual(['Acme', 'Zeta']);
    expect(data.vatRate).toBe(20);
    expect(invoiceService.create).toHaveBeenCalledWith(written as never);
    expect(component.invoices[0].id).toBe(9);
    expect(notifications.success).toHaveBeenCalledWith('invoices.created');
  });

  it('changes a draft, and leaves it when the dialog is cancelled', () => {
    const component = create().componentInstance;
    closing(undefined);
    component.write(draft);
    expect(invoiceService.update).not.toHaveBeenCalled();

    closing({ clientId: 3 });
    invoiceService.update.and.returnValue(of({ ...draft, total: 1500 }));
    component.write(draft);
    expect(component.invoices.find(item => item.id === 1)?.total).toBe(1500);
    expect(notifications.success).toHaveBeenCalledWith('invoices.saved');
  });

  it('issues a draft on the chosen date, and tells why the server refuses', () => {
    const component = create().componentInstance;
    closing('2026-10-27');
    invoiceService.issue.and.returnValue(of({ ...draft, status: 'ISSUED', number: 'F2026-0004' }));
    component.issue(draft);
    expect(invoiceService.issue).toHaveBeenCalledWith(1, '2026-10-27');
    expect(component.invoices.find(item => item.id === 1)?.number).toBe('F2026-0004');

    invoiceService.issue.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: { message: 'Complete the billing identity before issuing an invoice' } })));
    component.issue(draft);
    expect(notifications.error).toHaveBeenCalledWith('invoices.errors.billing');

    closing(undefined);
    invoiceService.issue.calls.reset();
    component.issue(draft);
    expect(invoiceService.issue).not.toHaveBeenCalled();
  });

  it('records a payment and takes it back', () => {
    const component = create().componentInstance;
    closing('2026-10-26');
    invoiceService.setPaid.and.returnValue(of({ ...issued, status: 'PAID', paidOn: '2026-10-26' }));
    component.markPaid(issued);
    expect(invoiceService.setPaid).toHaveBeenCalledWith(2, '2026-10-26');
    expect(notifications.success).toHaveBeenCalledWith('invoices.paidDone');

    closing(undefined);
    component.markPaid(issued);
    expect(invoiceService.setPaid).toHaveBeenCalledTimes(1);

    invoiceService.setPaid.and.returnValue(of({ ...paid, status: 'ISSUED', paidOn: null }));
    component.markUnpaid(paid);
    expect(invoiceService.setPaid).toHaveBeenCalledWith(4, null);
  });

  it('deletes a draft once confirmed', () => {
    const component = create().componentInstance;
    confirm.confirm.and.returnValue(of(false));
    component.remove(draft);
    expect(invoiceService.delete).not.toHaveBeenCalled();

    confirm.confirm.and.returnValue(of(true));
    invoiceService.delete.and.returnValue(of(undefined));
    component.remove(draft);
    expect(component.invoices.map(item => item.id)).toEqual([2, 3, 4]);

    invoiceService.delete.and.returnValue(throwError(() => new Error('500')));
    component.remove(issued);
    expect(notifications.error).toHaveBeenCalledWith('invoices.saveError');
  });

  it('downloads the PDF under its number in its language', () => {
    const component = create().componentInstance;
    const link = jasmine.createSpyObj('a', ['click']);
    spyOn(document, 'createElement').and.callFake(() => link as never);
    invoiceService.downloadPdf.and.returnValue(of(new Blob(['%PDF'])));

    component.download(paid);
    expect(link.download).toBe('invoice-F2026-0003.pdf');
    component.download(draft);
    expect(link.download).toBe('facture-draft-1.pdf');

    invoiceService.downloadPdf.and.returnValue(throwError(() => new Error('500')));
    component.download(paid);
    expect(notifications.error).toHaveBeenCalledWith('invoices.pdfError');
  });

  it('says when the clients cannot be read for a draft', () => {
    const component = create().componentInstance;
    TestBed.inject(ClientService).getForCurrentFreelance = () => throwError(() => new Error('500'));
    component.write();
    expect(dialog.open).not.toHaveBeenCalled();
    expect(notifications.error).toHaveBeenCalledWith('invoices.loadError');
  });

  it('says when the invoices cannot be read, and waits for a workspace', () => {
    create(throwError(() => new Error('500')));
    expect(notifications.error).toHaveBeenCalledWith('invoices.loadError');

    TestBed.resetTestingModule();
    user = null;
    const component = create().componentInstance;
    expect(component.loading).toBeFalse();
    expect(invoiceService.list).not.toHaveBeenCalled();
  });
});
