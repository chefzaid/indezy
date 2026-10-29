import { Offer } from '../../models/offer.models';

/** "550 € – 650 €", "≤ 600 €", "≥ 500 €" a day, or empty when the offer states no rate (the API leaves out missing values). */
export function offerRate(offer: Pick<Offer, 'rateMin' | 'rateMax'>, locale: string): string {
  const { rateMin, rateMax } = offer;
  const amount = (value: number): string => new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR', maximumFractionDigits: 0 })
    .format(value);
  if (rateMin !== undefined && rateMax !== undefined) {
    return rateMin === rateMax ? amount(rateMin) : `${amount(rateMin)} – ${amount(rateMax)}`;
  }
  if (rateMax !== undefined) {
    return `≤ ${amount(rateMax)}`;
  }
  return rateMin === undefined ? '' : `≥ ${amount(rateMin)}`;
}

/** "Courbevoie (92)", the department or region, or the country; empty when the offer names no place. */
export function offerPlace(offer: Pick<Offer, 'city' | 'departmentCode' | 'departmentName' | 'regionName' | 'countryCode'>): string {
  const { city, departmentCode, departmentName, regionName, countryCode } = offer;
  if (city) {
    return departmentCode && countryCode === 'FR' ? `${city} (${departmentCode})` : city;
  }
  return departmentName ?? regionName ?? (countryCode && countryCode !== 'FR' ? countryCode : '');
}

/** The translation key saying how the mission is worked: on site, remote, or hybrid with its remote days when stated. */
export function offerRemoteKey(offer: Pick<Offer, 'workMode' | 'remoteDaysPerWeek'>): string {
  if (offer.workMode === 'HYBRID') {
    return offer.remoteDaysPerWeek ? 'offers.card.remote.hybridDays' : 'offers.card.remote.HYBRID';
  }
  return `offers.card.remote.${offer.workMode}`;
}

/** The translation key of the mission's duration: one month, or a number of them. */
export function offerDurationKey(offer: Pick<Offer, 'durationMonths'>): string {
  return offer.durationMonths === 1 ? 'offers.card.oneMonth' : 'offers.card.months';
}

/** Whether the profile names this skill of the offer, case and accents aside, as the server compares skills. */
export function isMatchedSkill(offer: Pick<Offer, 'match'>, skill: string): boolean {
  const wanted = fold(skill);
  return offer.match?.skills.some(matched => fold(matched) === wanted) ?? false;
}

/** The offer's skills, those of the profile first, at most `count` of them. */
export function offerSkillsMatchedFirst(offer: Pick<Offer, 'skills' | 'match'>, count: number): string[] {
  const matched = offer.skills.filter(skill => isMatchedSkill(offer, skill));
  return [...matched, ...offer.skills.filter(skill => !matched.includes(skill))].slice(0, count);
}

function fold(text: string): string {
  return text.normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().trim();
}
