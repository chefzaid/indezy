import { HttpErrorResponse } from '@angular/common/http';

/** The refusals the server explains (in English), told in the interface language. */
const REFUSALS: [RegExp, string][] = [
  [/^Complete the billing identity/, 'invoices.errors.billing'],
  [/^The client needs an address/, 'invoices.errors.clientAddress'],
  [/^An invoice cannot be dated before the last one issued/, 'invoices.errors.dateBeforeLast'],
  [/^This activity report is already invoiced/, 'invoices.errors.alreadyInvoiced'],
  [/^Only an activity report with days worked/, 'invoices.errors.notInvoiceable'],
  [/^The mission has no daily rate to invoice/, 'invoices.errors.noDailyRate'],
  [/^The mission has no client to invoice/, 'invoices.errors.noClient'],
  [/^An issued invoice can no longer change/, 'invoices.errors.frozen'],
  [/^The invoice's client no longer exists/, 'invoices.errors.clientGone'],
  [/^A credit note cannot credit more than what is left/, 'invoices.errors.creditTooHigh'],
  [/^A credit note goes to the client of its invoice/, 'invoices.errors.creditClient']
];

/** The translation key telling why the server refused, or the given fallback. */
export function invoiceErrorKey(error: HttpErrorResponse | null | undefined, fallback: string): string {
  const message: unknown = error?.error?.message;
  if (error?.status === 409 && typeof message === 'string') {
    return REFUSALS.find(([pattern]) => pattern.test(message))?.[1] ?? fallback;
  }
  return fallback;
}
