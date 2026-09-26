import { AbstractControl } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';

/** Translated message for the first validation error of a touched control, or '' when valid. */
export function fieldError(control: AbstractControl | null, translate: TranslateService): string {
  const errors = control?.touched ? control.errors : null;
  if (!errors) {
    return '';
  }
  if (errors['required']) {
    return translate.instant('errors.fieldRequired');
  }
  if (errors['email']) {
    return translate.instant('errors.invalidEmail');
  }
  if (errors['minlength']) {
    return translate.instant('errors.minLength', { length: errors['minlength'].requiredLength });
  }
  if (errors['min']) {
    return translate.instant('errors.minValue', { value: errors['min'].min });
  }
  if (errors['max']) {
    return translate.instant('errors.maxValue', { value: errors['max'].max });
  }
  return '';
}
