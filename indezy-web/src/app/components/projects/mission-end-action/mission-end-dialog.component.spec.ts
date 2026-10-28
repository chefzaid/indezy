import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNativeDateAdapter } from '@angular/material/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';

import { MissionEndDialogComponent } from './mission-end-dialog.component';

describe('MissionEndDialogComponent', () => {
  function create(startDate?: string): { component: MissionEndDialogComponent; close: jasmine.Spy } {
    const dialogRef = jasmine.createSpyObj<MatDialogRef<MissionEndDialogComponent>>('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [MissionEndDialogComponent, NoopAnimationsModule, TranslateModule.forRoot()],
      providers: [
        provideNativeDateAdapter(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { role: 'Développeur Java', startDate } }
      ]
    });
    const fixture = TestBed.createComponent(MissionEndDialogComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, close: dialogRef.close };
  }

  it('ends today by default and needs a reason that says something', () => {
    const { component, close } = create('2026-01-05');

    expect((component.form.value.endedOn as Date).toDateString()).toBe(new Date().toDateString());
    component.form.patchValue({ reason: '   ' });
    component.confirm();
    expect(close).not.toHaveBeenCalled();

    component.form.patchValue({ endedOn: new Date(2026, 9, 31), reason: '  The client cut the budget ' });
    component.confirm();
    expect(close).toHaveBeenCalledWith({ endedOn: '2026-10-31', reason: 'The client cut the budget' });
  });

  it('does not end a mission before it starts', () => {
    const { component, close } = create('2026-01-05');

    expect(component.minDate).toEqual(new Date(2026, 0, 5));
    component.form.patchValue({ endedOn: new Date(2025, 11, 31), reason: 'Too early' });
    expect(component.form.get('endedOn')!.hasError('matDatepickerMin')).toBeTrue();
    component.confirm();
    expect(close).not.toHaveBeenCalled();
  });
});
