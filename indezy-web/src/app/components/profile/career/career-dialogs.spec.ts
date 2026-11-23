import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Education, Experience } from '../../../models';
import { EducationDialogComponent } from './education-dialog.component';
import { ExperienceDialogComponent } from './experience-dialog.component';

describe('Career dialogs', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<unknown>>;

  function create<T>(component: Type<T>, data: unknown): T {
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [component, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: data }]
    });
    const fixture = TestBed.createComponent(component);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('writes a position by the month, empty fields left out', () => {
    const experience: Experience = { id: 1, employer: 'Banque', title: 'Dev', startsOn: '2015-09-01', endsOn: '2019-06-30', shownPublicly: true };
    const dialog = create(ExperienceDialogComponent, experience);
    expect(dialog.form.getRawValue().endsOn).toBe('2019-06');
    dialog.form.patchValue({ title: ' Lead ', description: '  ', stack: 'Java ' });
    dialog.save();
    expect(dialogRef.close).toHaveBeenCalledWith({ title: 'Lead', employer: 'Banque', startsOn: '2015-09-01', endsOn: '2019-06-01',
      description: null, stack: 'Java', shownPublicly: true });
  });

  it('refuses a position without a title or ending before it starts', () => {
    const dialog = create(ExperienceDialogComponent, null);
    dialog.save();
    expect(dialogRef.close).not.toHaveBeenCalled();
    dialog.form.patchValue({ title: 'Dev', employer: 'Banque', startsOn: '2020-05', endsOn: '2019-01' });
    expect(dialog.form.hasError('endsBeforeStart')).toBeTrue();
    dialog.save();
    expect(dialogRef.close).not.toHaveBeenCalled();
    dialog.form.patchValue({ endsOn: '' });
    dialog.save();
    expect(dialogRef.close).toHaveBeenCalledWith(jasmine.objectContaining({ startsOn: '2020-05-01', endsOn: null, shownPublicly: false }));
  });

  it('writes a school with its years', () => {
    const education: Education = { id: 2, school: 'INSA', degree: null, startYear: 2010, endYear: null, shownPublicly: false };
    const dialog = create(EducationDialogComponent, education);
    dialog.form.patchValue({ degree: ' Ingénieure ', endYear: 2015 });
    dialog.save();
    expect(dialogRef.close).toHaveBeenCalledWith({ school: 'INSA', degree: 'Ingénieure', startYear: 2010, endYear: 2015, shownPublicly: false });
  });

  it('refuses a school without its name or ending before it starts', () => {
    const dialog = create(EducationDialogComponent, null);
    dialog.save();
    dialog.form.patchValue({ school: 'INSA', startYear: 2015, endYear: 2012 });
    dialog.save();
    expect(dialogRef.close).not.toHaveBeenCalled();
    dialog.form.patchValue({ endYear: null, shownPublicly: true });
    dialog.save();
    expect(dialogRef.close).toHaveBeenCalledWith({ school: 'INSA', degree: null, startYear: 2015, endYear: null, shownPublicly: true });
  });
});
