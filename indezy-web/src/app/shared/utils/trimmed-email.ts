import { AbstractControl, FormControl, ValidationErrors, Validators } from '@angular/forms';

/**
 * `Validators.email` on the value without its surrounding spaces, which the form trims before saving: an address pasted
 * with a stray space is accepted instead of reported invalid.
 */
export function trimmedEmail(control: AbstractControl): ValidationErrors | null {
  return Validators.email(new FormControl(typeof control.value === 'string' ? control.value.trim() : control.value));
}
