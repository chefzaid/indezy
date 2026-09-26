import { FormControl, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { fieldError } from './form-errors';

describe('fieldError', () => {
  const translate = {
    instant: (key: string, params?: Record<string, unknown>) => params ? `${key} ${JSON.stringify(params)}` : key
  } as unknown as TranslateService;

  const touched = (control: FormControl): FormControl => {
    control.markAsTouched();
    return control;
  };

  it('stays silent for untouched or valid controls', () => {
    expect(fieldError(new FormControl('', Validators.required), translate)).toBe('');
    expect(fieldError(touched(new FormControl('ok', Validators.required)), translate)).toBe('');
    expect(fieldError(null, translate)).toBe('');
  });

  it('translates the first error with its parameters', () => {
    expect(fieldError(touched(new FormControl('', Validators.required)), translate)).toBe('errors.fieldRequired');
    expect(fieldError(touched(new FormControl('x', Validators.email)), translate)).toBe('errors.invalidEmail');
    expect(fieldError(touched(new FormControl('ab', Validators.minLength(3))), translate))
      .toBe('errors.minLength {"length":3}');
    expect(fieldError(touched(new FormControl(0, Validators.min(1))), translate)).toBe('errors.minValue {"value":1}');
    expect(fieldError(touched(new FormControl(9, Validators.max(5))), translate)).toBe('errors.maxValue {"value":5}');
  });
});
