import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { InvoiceableReport } from '../../../models';
import { InvoiceReportDialogComponent } from './invoice-report-dialog.component';
import en from '../../../../assets/i18n/en.json';

describe('InvoiceReportDialogComponent', () => {
  const month = (reportId: number, changes: Partial<InvoiceableReport> = {}): InvoiceableReport => ({ reportId, projectId: 8,
    projectRole: 'Java lead', clientName: 'Capgemini', year: 2026, month: 11, status: 'VALIDATED', workedDays: 2.5, dailyRate: 600,
    amount: 1500, ...changes });

  function render(reports: InvoiceableReport[]): HTMLElement {
    TestBed.configureTestingModule({
      imports: [InvoiceReportDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: reports },
        { provide: MatDialogRef, useValue: jasmine.createSpyObj('MatDialogRef', ['close']) }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(InvoiceReportDialogComponent);
    fixture.detectChanges();
    return fixture.nativeElement;
  }

  const text = (element: Element | null) => element!.textContent!.replace(/\s+/g, ' ').trim();

  it('lists each month with its mission, status and days worked times the daily rate', () => {
    const element = render([month(1), month(2, { status: 'DRAFT', month: 10, clientName: null }),
      month(3, { dailyRate: null, amount: null })]);

    const rows = element.querySelectorAll('[data-invoiceable-report]');
    expect(text(rows[0].querySelector('.mission'))).toBe('Java lead · Capgemini');
    expect(text(rows[0].querySelector('.month'))).toBe('November 2026 · Approved');
    expect(text(rows[0].querySelector('.amount'))).toBe('2.5 days × 600 € = 1,500.00 € before VAT');
    expect(rows[0].querySelector<HTMLButtonElement>('[data-invoice-this-report]')!.disabled).toBeFalse();
    expect(text(rows[1].querySelector('.month'))).toBe('October 2026 · Draft');
    expect(text(rows[1].querySelector('.missing'))).toBe('Add the mission\'s client to invoice this month.');
    expect(rows[1].querySelector<HTMLButtonElement>('[data-invoice-this-report]')!.disabled).toBeTrue();
    expect(text(rows[2].querySelector('.missing'))).toBe('Add the mission\'s daily rate to invoice this month.');
    expect(rows[2].querySelector<HTMLButtonElement>('[data-invoice-this-report]')!.disabled).toBeTrue();
  });

  it('says when no month is left to invoice', () => {
    expect(text(render([]).querySelector('[data-no-invoiceable-report]'))).toContain('No activity report to invoice');
  });
});
