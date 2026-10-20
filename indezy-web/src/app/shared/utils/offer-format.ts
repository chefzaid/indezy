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
