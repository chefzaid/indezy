import { HttpErrorResponse } from '@angular/common/http';
import { invoiceErrorKey } from './invoice-errors';

describe('invoiceErrorKey', () => {
  const conflict = (message: unknown) => new HttpErrorResponse({ status: 409, error: { message } });

  it('tells the refusals the server explains', () => {
    expect(invoiceErrorKey(conflict('Complete the billing identity before issuing an invoice'), 'x')).toBe('invoices.errors.billing');
    expect(invoiceErrorKey(conflict('An invoice cannot be dated before the last one issued, on 2026-10-01'), 'x'))
      .toBe('invoices.errors.dateBeforeLast');
    expect(invoiceErrorKey(conflict('This activity report is already invoiced'), 'x')).toBe('invoices.errors.alreadyInvoiced');
    expect(invoiceErrorKey(conflict('Only an activity report with days worked, and not rejected, can be invoiced'), 'x'))
      .toBe('invoices.errors.notInvoiceable');
    expect(invoiceErrorKey(conflict('The mission has no client to invoice'), 'x')).toBe('invoices.errors.noClient');
  });

  it('falls back on anything else', () => {
    expect(invoiceErrorKey(conflict('Something new'), 'fallback')).toBe('fallback');
    expect(invoiceErrorKey(conflict(42), 'fallback')).toBe('fallback');
    expect(invoiceErrorKey(new HttpErrorResponse({ status: 500 }), 'fallback')).toBe('fallback');
    expect(invoiceErrorKey(null, 'fallback')).toBe('fallback');
  });
});
