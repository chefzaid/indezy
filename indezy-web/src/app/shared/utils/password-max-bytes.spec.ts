import { FormControl } from '@angular/forms';
import { PASSWORD_MAX_BYTES, passwordMaxBytes } from './password-max-bytes';

describe('passwordMaxBytes', () => {
  const validate = (value: unknown) => passwordMaxBytes(new FormControl(value));

  it('accepts passwords up to 72 bytes, whatever their character count', () => {
    expect(PASSWORD_MAX_BYTES).toBe(72);
    expect(validate('a'.repeat(72))).toBeNull();
    expect(validate('é'.repeat(36))).toBeNull();
    expect(validate('🔒'.repeat(18))).toBeNull();
    expect(validate('')).toBeNull();
    expect(validate(null)).toBeNull();
  });

  it('counts UTF-8 bytes, so fewer than 72 characters can exceed the limit', () => {
    expect(validate('a'.repeat(80))).toEqual({ maxBytes: { max: 72, actual: 80 } });
    expect(validate('é'.repeat(37))).toEqual({ maxBytes: { max: 72, actual: 74 } });
    expect(validate('🔒'.repeat(18) + '!')).toEqual({ maxBytes: { max: 72, actual: 73 } });
  });
});
