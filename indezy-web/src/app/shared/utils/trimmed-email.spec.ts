import { FormControl } from '@angular/forms';
import { trimmedEmail } from './trimmed-email';

describe('trimmedEmail', () => {
  const validate = (value: unknown) => trimmedEmail(new FormControl(value));

  it('accepts an address with surrounding spaces, and no address', () => {
    expect(validate(' bob@acme.fr ')).toBeNull();
    expect(validate('bob@acme.fr')).toBeNull();
    expect(validate('')).toBeNull();
    expect(validate(null)).toBeNull();
  });

  it('refuses what is not an address', () => {
    expect(validate(' bob ')).toEqual({ email: true });
    expect(validate('bob @acme.fr')).toEqual({ email: true });
  });
});
