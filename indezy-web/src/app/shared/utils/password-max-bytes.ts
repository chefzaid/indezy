import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * The server hashes passwords with BCrypt, which takes at most 72 bytes of their UTF-8 encoding: an accented letter
 * uses 2 of them and an emoji 4, so a password under 72 characters can still be too long.
 */
export const PASSWORD_MAX_BYTES = 72;

const utf8 = new TextEncoder();

/** Fails with `{ maxBytes: { max, actual } }` when the password's UTF-8 encoding exceeds {@link PASSWORD_MAX_BYTES}. */
export function passwordMaxBytes(control: AbstractControl): ValidationErrors | null {
  if (typeof control.value !== 'string') {
    return null;
  }
  const actual = utf8.encode(control.value).length;
  return actual > PASSWORD_MAX_BYTES ? { maxBytes: { max: PASSWORD_MAX_BYTES, actual } } : null;
}
