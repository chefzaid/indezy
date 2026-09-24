import { InterviewStepDialogComponent } from './interview-step-dialog.component';

describe('InterviewStepDialogComponent helpers', () => {
  it('sends the chosen wall-clock time without a time zone', () => {
    expect(InterviewStepDialogComponent.toLocalDateTime(new Date(2026, 9, 20), '10:30'))
      .toBe('2026-10-20T10:30:00');
  });

  it('defaults to 09:00 when no time is given and omits missing dates', () => {
    expect(InterviewStepDialogComponent.toLocalDateTime(new Date(2026, 0, 5), ''))
      .toBe('2026-01-05T09:00:00');
    expect(InterviewStepDialogComponent.toLocalDateTime(null, '10:00')).toBeUndefined();
  });

  it('formats times for the time input', () => {
    expect(InterviewStepDialogComponent.toTimeInput(new Date(2026, 0, 5, 7, 5))).toBe('07:05');
  });
});
