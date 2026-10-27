import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/** The value without spaces, in upper case when asked: how billing identifiers are compared and stored. */
export function compactId(value: string | null | undefined, upperCase = false): string {
  const compact = (value ?? '').replace(/\s+/g, '');
  return upperCase ? compact.toUpperCase() : compact;
}

/** Whether an IBAN (compact, upper case) has a valid shape and ISO 13616 check digits. */
export function isIban(iban: string): boolean {
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban)) {
    return false;
  }
  const digits = (iban.slice(4) + iban.slice(0, 4)).replace(/[A-Z]/g, letter => String(letter.charCodeAt(0) - 55));
  // Mod 97 piece by piece: the number is too long for a double.
  return digits.match(/\d{1,7}/g)!.reduce((rest, piece) => Number(String(rest) + piece) % 97, 0) === 1;
}

const PATTERNS: Record<string, RegExp> = {
  siren: /^\d{9}$/,
  siret: /^\d{14}$/,
  vatNumber: /^[A-Z]{2}[0-9A-Z]{2,13}$/,
  bic: /^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$/
};

/** Checks a billing identifier typed with or without spaces; an empty field passes. Fails with {@code { billing: kind }}. */
export function billingId(kind: 'siren' | 'siret' | 'vatNumber' | 'bic' | 'iban'): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = compactId(control.value, kind !== 'siren' && kind !== 'siret');
    if (!value) {
      return null;
    }
    const valid = kind === 'iban' ? isIban(value) : PATTERNS[kind].test(value);
    return valid ? null : { billing: kind };
  };
}
