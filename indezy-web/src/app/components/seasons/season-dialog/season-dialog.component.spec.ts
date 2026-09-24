import { FormBuilder } from '@angular/forms';
import { endAfterStartValidator, periodOf } from './season-dialog.component';

describe('SeasonDialogComponent helpers', () => {
  it('names the calendar period of a date', () => {
    expect(periodOf(new Date(2026, 0, 10))).toBe('winter');
    expect(periodOf(new Date(2026, 3, 10))).toBe('spring');
    expect(periodOf(new Date(2026, 6, 10))).toBe('summer');
    expect(periodOf(new Date(2026, 9, 10))).toBe('autumn');
    expect(periodOf(new Date(2026, 11, 10))).toBe('winter');
  });

  it('rejects an end date before the start date', () => {
    const fb = new FormBuilder();
    const form = (start: Date | null, end: Date | null) =>
      fb.group({ startDate: [start], endDate: [end] }, { validators: endAfterStartValidator });

    expect(form(new Date(2026, 5, 10), new Date(2026, 5, 1)).hasError('endBeforeStart')).toBeTrue();
    expect(form(new Date(2026, 5, 1), new Date(2026, 5, 10)).hasError('endBeforeStart')).toBeFalse();
    expect(form(new Date(2026, 5, 1), null).hasError('endBeforeStart')).toBeFalse();
  });
});
