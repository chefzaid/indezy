// Saved and suggested searches in words (see docs/features.md#suggested-searches)

import { TranslateService } from '@ngx-translate/core';
import { OfferQuery } from '../../models/offer.models';

/** Longest name a saved search takes. */
export const SEARCH_NAME_LENGTH = 80;

/**
 * What a search keeps, one phrase per filter it sets ("Java + Kafka", "600 €/day or more", "Lyon + 30 km", "Full
 * remote"...), in the interface's language; `place` names the city a distance is measured from, or the region kept.
 */
export function describeOfferSearch(filters: Partial<OfferQuery>, place: string | null | undefined, translate: TranslateService,
                                    locale: string): string[] {
  const parts: string[] = [];
  const say = (key: string, params?: Record<string, unknown>): void => {
    parts.push(translate.instant(`offers.searchParts.${key}`, params));
  };
  if (filters.q) {
    say('q', { q: filters.q });
  }
  if (filters.skills?.length) {
    parts.push(filters.skills.join(' + '));
  }
  if (filters.rateMin) {
    say('rateMin', { rate: new Intl.NumberFormat(locale).format(filters.rateMin) });
  }
  if (filters.radiusKm) {
    say(place ? 'near' : 'within', { place, km: filters.radiusKm });
  } else if (filters.regions?.length && place) {
    parts.push(place);
  }
  if (filters.workModes?.length === 1 && filters.workModes[0] === 'REMOTE') {
    say('remote');
  } else if (filters.workModes?.includes('HYBRID')) {
    say(filters.minRemoteDays ? 'hybridDays' : 'hybrid', { days: filters.minRemoteDays });
  }
  if (filters.startBefore) {
    const day = new Date(`${filters.startBefore}T00:00:00`);
    say('startBefore', { date: new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short' }).format(day) });
  }
  return parts;
}

/** A name for the search, from its phrases, as long as a saved search's name may be. */
export function offerSearchName(parts: string[]): string {
  const name = parts.join(' · ');
  return name.length <= SEARCH_NAME_LENGTH ? name : `${name.slice(0, SEARCH_NAME_LENGTH - 1).trimEnd()}…`;
}
