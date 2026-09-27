import { FormBuilder } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { TranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { Season } from '../../../models/season.models';
import { SeasonService } from '../../../services/season/season.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { endAfterStartValidator, periodOf, SeasonDialogComponent } from './season-dialog.component';

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

describe('SeasonDialogComponent submissions', () => {
  let service: jasmine.SpyObj<SeasonService>;
  let dialog: jasmine.SpyObj<MatDialogRef<SeasonDialogComponent, Season>>;
  let notification: jasmine.SpyObj<NotificationService>;
  let translate: jasmine.SpyObj<TranslateService>;
  const saved: Season = { id: 9, name: 'Autumn', startDate: '2026-09-01', freelanceId: 1 };

  beforeEach(() => {
    service = jasmine.createSpyObj('SeasonService', ['create', 'update']);
    dialog = jasmine.createSpyObj('MatDialogRef', ['close']);
    notification = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    translate = jasmine.createSpyObj('TranslateService', ['instant']);
    translate.instant.and.returnValue('Autumn search 2026');
  });

  function create(season?: Season, defaultTargetDailyRate?: number): SeasonDialogComponent {
    return new SeasonDialogComponent(new FormBuilder(), dialog, { freelanceId: 1, season, defaultTargetDailyRate },
      service, notification, translate);
  }

  it('prefills new seasons from the profile and permits a different target', () => {
    service.create.and.returnValue(of(saved));
    const component = create(undefined, 650);
    expect(component.seasonForm.value.targetDailyRate).toBe(650);
    component.seasonForm.patchValue({ targetDailyRate: 700 });
    component.onSubmit();
    expect(service.create.calls.mostRecent().args[0].targetDailyRate).toBe(700);
  });

  it('preserves the target of an existing season, including no target', () => {
    expect(create(saved, 650).seasonForm.value.targetDailyRate).toBeNull();
    expect(create({ ...saved, targetDailyRate: 600 }, 650).seasonForm.value.targetDailyRate).toBe(600);
  });

  it('rejects invalid target rates while keeping the field optional', () => {
    const control = create().seasonForm.get('targetDailyRate')!;
    for (const value of [0, -1, 650.5, 2147483648]) {
      control.setValue(value);
      expect(control.invalid).toBeTrue();
    }
    control.setValue(null);
    expect(control.valid).toBeTrue();
  });

  it('creates a season with trimmed fields, local dates and optional values', () => {
    service.create.and.returnValue(of(saved));
    const component = create();
    component.seasonForm.patchValue({ name: ' Autumn ', startDate: new Date(2026, 8, 1), objective: ' ' });
    component.onSubmit();
    expect(service.create).toHaveBeenCalledOnceWith({
      name: 'Autumn', startDate: '2026-09-01', endDate: null, targetDailyRate: null, objective: null, freelanceId: 1
    });
    expect(dialog.close).toHaveBeenCalledOnceWith(saved);
    expect(notification.success).toHaveBeenCalledWith('seasons.createdSuccess');
  });

  it('updates an existing season while preserving its target and objective', () => {
    service.update.and.returnValue(of(saved));
    const component = create({ ...saved, endDate: '2026-10-01', targetDailyRate: 650, objective: 'Remote mission' });
    component.onSubmit();
    expect(service.update).toHaveBeenCalledOnceWith(9, {
      name: 'Autumn', startDate: '2026-09-01', endDate: '2026-10-01',
      targetDailyRate: 650, objective: 'Remote mission', freelanceId: 1
    });
    expect(notification.success).toHaveBeenCalledWith('seasons.updatedSuccess');
    expect(service.create).not.toHaveBeenCalled();
  });

  it('blocks invalid dates and duplicate submissions', () => {
    const component = create();
    component.seasonForm.patchValue({ startDate: new Date(2026, 8, 1), endDate: new Date(2026, 7, 1) });
    component.onSubmit();
    expect(service.create).not.toHaveBeenCalled();
    expect(component.seasonForm.touched).toBeTrue();

    const pending = new Subject<Season>();
    service.create.and.returnValue(pending);
    component.seasonForm.patchValue({ endDate: null });
    component.onSubmit();
    component.onSubmit();
    expect(service.create).toHaveBeenCalledTimes(1);
    pending.next(saved);
    pending.complete();
  });

  it('keeps the form open on failure so saving can be retried', () => {
    service.create.and.returnValue(throwError(() => new Error('offline')));
    const component = create();
    component.onSubmit();
    expect(component.isSubmitting).toBeFalse();
    expect(notification.error).toHaveBeenCalledWith('seasons.saveError');
    expect(dialog.close).not.toHaveBeenCalled();
    component.onCancel();
    expect(dialog.close).toHaveBeenCalledWith();
  });
});
