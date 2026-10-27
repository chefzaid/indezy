import { FormControl } from '@angular/forms';
import { billingId, compactId, isIban } from './billing-validators';

describe('billing validators', () => {
  it('compacts identifiers as people type them', () => {
    expect(compactId(' 552 100 554 ')).toBe('552100554');
    expect(compactId('fr 40 552100554', true)).toBe('FR40552100554');
    expect(compactId(null)).toBe('');
  });

  it('checks an IBAN\'s check digits', () => {
    expect(isIban('FR7630006000011234567890189')).toBeTrue();
    expect(isIban('DE89370400440532013000')).toBeTrue();
    expect(isIban('FR7630006000011234567890188')).toBeFalse();
    expect(isIban('FR76')).toBeFalse();
  });

  it('accepts each identifier typed with spaces, an empty field, and refuses a malformed one', () => {
    const check = (kind: Parameters<typeof billingId>[0], value: string) => billingId(kind)(new FormControl(value));
    expect(check('siren', '552 100 554')).toBeNull();
    expect(check('siret', '552 100 554 00012')).toBeNull();
    expect(check('vatNumber', 'fr40552100554')).toBeNull();
    expect(check('bic', 'bnpafrpp')).toBeNull();
    expect(check('iban', 'fr76 3000 6000 0112 3456 7890 189')).toBeNull();
    expect(check('siren', '')).toBeNull();
    expect(check('siren', '5521')).toEqual({ billing: 'siren' });
    expect(check('siret', '552100554')).toEqual({ billing: 'siret' });
    expect(check('vatNumber', '40552100554')).toEqual({ billing: 'vatNumber' });
    expect(check('bic', 'BNP')).toEqual({ billing: 'bic' });
    expect(check('iban', 'FR7630006000011234567890188')).toEqual({ billing: 'iban' });
  });
});
