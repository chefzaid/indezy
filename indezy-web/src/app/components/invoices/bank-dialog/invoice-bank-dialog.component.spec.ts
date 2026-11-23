import { TestBed } from '@angular/core/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { BankMatch, Invoice } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { InvoiceBankDialogComponent, STATEMENT_MAX_BYTES } from './invoice-bank-dialog.component';
import en from '../../../../assets/i18n/en.json';

describe('InvoiceBankDialogComponent', () => {
  const matches: BankMatch[] = [
    { date: '2026-11-20', label: 'VIR SEPA FACT F2026 0001', amount: 1234.56, invoiceId: 7, invoiceNumber: 'F2026-0001', clientName: 'Acme',
      reason: 'NUMBER' },
    { date: '2026-11-21', label: 'REMISE CHEQUE', amount: 77.77, invoiceId: null, invoiceNumber: null, clientName: null, reason: null },
    { date: '2026-11-22', label: 'VIR BETA', amount: 900, invoiceId: 8, invoiceNumber: 'F2026-0002', clientName: 'Beta', reason: 'CLIENT' }];

  function create() {
    const invoices = jasmine.createSpyObj('InvoiceService', ['reconcile', 'setPaid']);
    const dialog = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [InvoiceBankDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: InvoiceService, useValue: invoices }, { provide: MatDialogRef, useValue: dialog },
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(InvoiceBankDialogComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, invoices, dialog };
  }

  it('lists the credits with their invoices ticked, then marks those kept paid on their day', () => {
    const { fixture, component, invoices, dialog } = create();
    invoices.reconcile.and.returnValue(of(matches));
    component.match('Date;Libellé;Montant');
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('[data-bank-summary]')!.textContent).toContain('3 credit(s) read, 2 matched with an invoice.');
    expect(element.querySelector('[data-bank-credit="0"]')!.textContent).toContain('Its number is in the label');
    expect(element.querySelector('[data-bank-credit="1"]')!.textContent).toContain('No invoice found');
    expect([...component.kept]).toEqual([0, 2]);
    expect(element.querySelector('[data-bank-mark-paid]')!.textContent).toContain('Mark 2 invoice(s) paid');

    component.toggle(2);
    expect([...component.kept]).toEqual([0]);
    const paid = { id: 7, status: 'PAID' } as Invoice;
    invoices.setPaid.and.returnValue(of(paid));
    component.markPaid();
    expect(invoices.setPaid).toHaveBeenCalledOnceWith(7, '2026-11-20');
    expect(dialog.close).toHaveBeenCalledWith([paid]);
  });

  it('says when the statement holds no credit, is too large or cannot be read or saved', () => {
    const { fixture, component, invoices } = create();
    invoices.reconcile.and.returnValue(of([]));
    component.match('x');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-bank-none]')).not.toBeNull();

    const big = new File(['x'], 'big.csv');
    Object.defineProperty(big, 'size', { value: STATEMENT_MAX_BYTES + 1 });
    component.read({ target: { files: [big] } } as unknown as Event);
    expect(component.error).toBe('invoices.bank.tooLarge');
    component.read({ target: { files: [] } } as unknown as Event);

    invoices.reconcile.and.returnValue(throwError(() => new Error('400')));
    component.match('x');
    expect(component.error).toBe('invoices.bank.readError');

    invoices.reconcile.and.returnValue(of(matches));
    component.match('x');
    invoices.setPaid.and.returnValue(throwError(() => new Error('409')));
    component.markPaid();
    expect(component.error).toBe('invoices.bank.saveError');
    expect(component.saving).toBeFalse();
  });

  it('reads the chosen file before matching it', async () => {
    const { component, invoices } = create();
    invoices.reconcile.and.returnValue(of(matches));
    component.read({ target: { files: [new File(['Date;Libellé;Montant\n'], 's.csv')] } } as unknown as Event);
    await new Promise(resolve => setTimeout(resolve, 20));
    expect(invoices.reconcile).toHaveBeenCalledWith('Date;Libellé;Montant\n');
  });
});
