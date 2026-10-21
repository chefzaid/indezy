// What changed on an offer, in words (see docs/features.md#mission-history)

import { TranslateService } from '@ngx-translate/core';
import { OfferChange } from '../../models/offer.models';

/** The best rate of a "min|max" value: the top of its range, or the one rate it states. */
function bestRate(value: string | undefined): number | undefined {
  if (!value) {
    return undefined;
  }
  const [min, max] = value.split('|').map(part => (part ? Number(part) : undefined));
  return max ?? min;
}

/** "Rate raised from €500 to €550", "Start moved to Nov 2", "Description rewritten"... in the interface's language. */
export function describeOfferChange(change: OfferChange, translate: TranslateService, locale: string): string {
  const euros = (value: number): string => new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
    .format(value);
  if (change.field === 'RATE') {
    const before = bestRate(change.before);
    const after = bestRate(change.after);
    if (after === undefined) {
      return translate.instant('offers.history.rateChanged');
    }
    if (before === undefined) {
      return translate.instant('offers.history.rateStated', { after: euros(after) });
    }
    const key = after > before ? 'rateRaised' : after < before ? 'rateLowered' : 'rateChanged';
    return translate.instant(`offers.history.${key}`, { before: euros(before), after: euros(after) });
  }
  if (change.field === 'START') {
    if (change.after === 'ASAP') {
      return translate.instant('offers.history.startAsap');
    }
    const day = change.after ? new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
      .format(new Date(`${change.after}T00:00:00`)) : '';
    return translate.instant(change.before ? 'offers.history.startMoved' : 'offers.history.startStated', { day });
  }
  return translate.instant('offers.history.description');
}
