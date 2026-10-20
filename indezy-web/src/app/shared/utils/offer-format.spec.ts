import { offerPlace, offerRate } from './offer-format';

describe('offer format', () => {
  const plain = (value: string): string => value.replace(/\s/g, ' ');

  it('writes the daily rate the offer states', () => {
    expect(plain(offerRate({ rateMin: 550, rateMax: 650 }, 'fr-FR'))).toBe('550 € – 650 €');
    expect(plain(offerRate({ rateMin: 600, rateMax: 600 }, 'fr-FR'))).toBe('600 €');
    expect(plain(offerRate({ rateMax: 600 }, 'fr-FR'))).toBe('≤ 600 €');
    expect(offerRate({ rateMin: 500 }, 'en-US')).toBe('≥ €500');
    expect(offerRate({}, 'fr-FR')).toBe('');
  });

  it('writes the place the offer names', () => {
    expect(offerPlace({ city: 'Courbevoie', departmentCode: '92', countryCode: 'FR' })).toBe('Courbevoie (92)');
    expect(offerPlace({ city: 'Bruxelles', countryCode: 'BE' })).toBe('Bruxelles');
    expect(offerPlace({ departmentName: 'Hauts-de-Seine' })).toBe('Hauts-de-Seine');
    expect(offerPlace({ regionName: 'Île-de-France' })).toBe('Île-de-France');
    expect(offerPlace({ countryCode: 'CH' })).toBe('CH');
    expect(offerPlace({ countryCode: 'FR' })).toBe('');
  });
});
