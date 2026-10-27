import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';

import { BookingSettings } from '../../../models';
import { BookingSettingsDialogComponent } from './booking-settings-dialog.component';

describe('BookingSettingsDialogComponent', () => {
  let close: jasmine.Spy;

  function create(settings: BookingSettings): BookingSettingsDialogComponent {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [BookingSettingsDialogComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations(), { provide: MAT_DIALOG_DATA, useValue: settings }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(BookingSettingsDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const settings: BookingSettings = { enabled: true, title: 'Call', durationMinutes: 30, bufferMinutes: 15, minNoticeHours: 24,
    maxDaysAhead: 21, windows: [{ dayOfWeek: 'TUESDAY', start: '09:30:00', end: '12:00:00' }] };

  it('opens with the saved hours and gives back the open days', () => {
    const dialog = create(settings);
    expect(dialog.days.length).toBe(7);
    expect(dialog.days.at(1).getRawValue()).toEqual({ day: 'TUESDAY', open: true, start: '09:30', end: '12:00' });
    expect(dialog.days.at(0).getRawValue()).toEqual({ day: 'MONDAY', open: false, start: '09:00', end: '18:00' });

    dialog.days.at(0).patchValue({ open: true, start: '14:00' });
    dialog.form.patchValue({ title: '  Intro call ', durationMinutes: 45 });
    dialog.save();
    expect(close).toHaveBeenCalledWith({ enabled: true, title: 'Intro call', durationMinutes: 45, bufferMinutes: 15, minNoticeHours: 24,
      maxDaysAhead: 21, windows: [{ dayOfWeek: 'MONDAY', start: '14:00', end: '18:00' }, { dayOfWeek: 'TUESDAY', start: '09:30', end: '12:00' }] });
  });

  it('refuses hours ending before they start, no open day while on, and no title', () => {
    const dialog = create(settings);
    dialog.days.at(1).patchValue({ end: '09:00' });
    dialog.save();
    expect(dialog.hoursError).toBe('order');
    dialog.days.at(1).patchValue({ open: false });
    dialog.save();
    expect(dialog.hoursError).toBe('none');
    dialog.form.patchValue({ enabled: false, title: ' ' });
    dialog.form.controls.title.setValue('');
    dialog.save();
    expect(dialog.hoursError).toBeNull();
    expect(close).not.toHaveBeenCalled();
    dialog.form.controls.title.setValue('Off for now');
    dialog.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ enabled: false, windows: [] }));
  });

  it('starts from an empty title', () => {
    expect(create({ ...settings, title: '' }).form.controls.title.value).toBe('');
  });
});
