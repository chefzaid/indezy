import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { Invoice } from '../../../models';
import { ClientSharesComponent } from './client-shares.component';
import { clientShares } from './client-shares';
import en from '../../../../assets/i18n/en.json';

describe('client shares', () => {
  const today = new Date('2026-10-31T12:00:00');
  const invoice = (id: number, clientId: number, total: number, issueDate: string | null, extra: Partial<Invoice> = {}): Invoice =>
    ({ id, kind: 'INVOICE', status: 'ISSUED', clientId, clientName: `Client ${clientId}`, issueDate, totalExcludingVat: total,
      ...extra }) as Invoice;
  const invoices = [
    invoice(1, 1, 6000, '2026-10-02'),
    invoice(2, 1, 6000, '2026-09-01', { status: 'PAID' }),
    invoice(3, 2, 2000, '2026-05-10'),
    // A credit note takes off its client's revenue; a draft, an old invoice and a cancelled client count nothing.
    invoice(4, 1, 1000, '2026-10-10', { kind: 'CREDIT_NOTE' }),
    invoice(5, 2, 9999, null, { status: 'DRAFT' }),
    invoice(6, 3, 5000, '2025-10-31'),
    invoice(7, 4, 500, '2026-06-01'),
    invoice(8, 4, 500, '2026-07-01', { kind: 'CREDIT_NOTE' })
  ];

  it('shares the last 12 months\' revenue between clients, net of credit notes, largest first', () => {
    expect(clientShares(invoices, today)).toEqual([
      { clientId: 1, clientName: 'Client 1', revenue: 11000, percent: 85 },
      { clientId: 2, clientName: 'Client 2', revenue: 2000, percent: 15 }
    ]);
    expect(clientShares([], today)).toEqual([]);
    expect(clientShares([invoice(9, 1, 100, '2026-10-01', { kind: 'CREDIT_NOTE' })], today)).toEqual([]);
  });

  it('warns when one client pays half the revenue or more', () => {
    TestBed.configureTestingModule({ imports: [ClientSharesComponent, TranslateModule.forRoot()], providers: [provideRouter([])] });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ClientSharesComponent);
    fixture.componentInstance.invoices = [invoice(1, 1, 6000, new Date().toISOString().slice(0, 10)),
      invoice(2, 2, 4000, new Date().toISOString().slice(0, 10))];
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.querySelectorAll('[data-client-share]').length).toBe(2);
    expect(element.querySelector('[data-client-dependence]')!.textContent).toContain('Client 1 paid 60 % of your revenue');
    expect(element.querySelector('[data-client-share="1"] a')!.getAttribute('href')).toBe('/clients/1');

    fixture.componentInstance.invoices = [invoice(1, 1, 4000, new Date().toISOString().slice(0, 10)),
      invoice(2, 2, 3000, new Date().toISOString().slice(0, 10)), invoice(3, 3, 3000, new Date().toISOString().slice(0, 10))];
    fixture.detectChanges();
    expect(element.querySelector('[data-client-dependence]')).toBeNull();

    fixture.componentInstance.invoices = [];
    fixture.detectChanges();
    expect(element.querySelector('[data-client-shares]')).toBeNull();
  });
});
