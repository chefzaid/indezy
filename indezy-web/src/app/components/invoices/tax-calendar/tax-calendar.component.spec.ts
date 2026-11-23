import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

import { TaxDeadline } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { TaxCalendarComponent } from './tax-calendar.component';

describe('TaxCalendarComponent', () => {
  function create(deadlines: TaxDeadline[]): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [TaxCalendarComponent, TranslateModule.forRoot()],
      providers: [{ provide: InvoiceService, useValue: { taxCalendar: () => of(deadlines) } }]
    });
    const fixture = TestBed.createComponent(TaxCalendarComponent);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('lists the deadlines, the VAT instalments by month with their estimate', () => {
    const element = create([
      { kind: 'CFE', dueOn: '2026-12-15', monthOnly: false, amount: null },
      { kind: 'VAT_INSTALMENT', dueOn: '2027-07-31', monthOnly: true, amount: 1650 }]);
    expect(element.querySelector('[data-tax-deadline="CFE"]')!.textContent).toContain('15 December 2026');
    const vat = element.querySelector('[data-tax-deadline="VAT_INSTALMENT"]')!.textContent!;
    expect(vat).toContain('July 2027');
    expect(vat).toContain('1,650 €');
  });

  it('stays away without a deadline', () => {
    expect(create([]).querySelector('[data-tax-calendar]')).toBeNull();
  });
});
