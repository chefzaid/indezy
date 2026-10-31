import { TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';

import { PaymentDelay } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { PaymentDelayComponent } from './payment-delay.component';
import en from '../../../../assets/i18n/en.json';

describe('PaymentDelayComponent', () => {
  const delays: PaymentDelay[] = [{ clientId: 3, paidInvoices: 4, averageDays: 52, averageDaysLate: 12 },
    { clientId: 5, paidInvoices: 1, averageDays: 20, averageDaysLate: 0 }];

  function show(clientId: number | null, answer: Observable<PaymentDelay[]> = of(delays)): HTMLElement {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [PaymentDelayComponent, TranslateModule.forRoot()],
      providers: [{ provide: InvoiceService, useValue: { paymentDelays: () => answer } }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(PaymentDelayComponent);
    fixture.componentRef.setInput('clientId', clientId);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('says how the client pays, late or on time', () => {
    const late = show(3).querySelector('[data-payment-delay]')!;
    expect(late.textContent).toContain('Pays in 52 days on average, 12 days late (4 paid invoice(s))');
    expect(late.classList).toContain('late');
    expect(show(5).querySelector('[data-payment-delay]')!.textContent).toContain('on time');
  });

  it('shows nothing without a paid invoice, a client or an answer', () => {
    expect(show(9).querySelector('[data-payment-delay]')).toBeNull();
    expect(show(null).querySelector('[data-payment-delay]')).toBeNull();
    expect(show(3, throwError(() => new Error('403'))).querySelector('[data-payment-delay]')).toBeNull();
  });
});
