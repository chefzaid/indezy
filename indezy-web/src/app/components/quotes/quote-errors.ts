import { HttpErrorResponse } from '@angular/common/http';

/** The refusals the server explains (in English), told in the interface language. */
const REFUSALS: [RegExp, string][] = [
  [/^Complete the billing identity/, 'quotes.errors.billing'],
  [/^The client needs an address/, 'quotes.errors.clientAddress'],
  [/^A sent quote can no longer change/, 'quotes.errors.frozen'],
  [/^The quote's client no longer exists/, 'quotes.errors.clientGone'],
  [/^Only a sent quote can be accepted or refused/, 'quotes.errors.notSent'],
  [/^Only an accepted quote can be invoiced/, 'quotes.errors.notAccepted'],
  [/^This quote is already invoiced/, 'quotes.errors.alreadyInvoiced'],
  [/^The quote asks no deposit/, 'quotes.errors.noDeposit'],
  [/^The deposit of this quote is already invoiced/, 'quotes.errors.depositInvoiced'],
  [/^Issue the deposit invoice before invoicing the balance/, 'quotes.errors.depositDraft'],
  [/^A quote cannot total less than zero/, 'quotes.errors.negative']
];

/** The translation key telling why the server refused, or the given fallback. */
export function quoteErrorKey(error: HttpErrorResponse | null | undefined, fallback: string): string {
  const message: unknown = error?.error?.message;
  if (error?.status === 409 && typeof message === 'string') {
    return REFUSALS.find(([pattern]) => pattern.test(message))?.[1] ?? fallback;
  }
  return fallback;
}
