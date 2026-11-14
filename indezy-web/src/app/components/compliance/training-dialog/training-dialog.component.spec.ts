import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Training } from '../../../models';
import { TrainingDialogComponent } from './training-dialog.component';

describe('TrainingDialogComponent', () => {
  let close: jasmine.Spy;

  function create(training: Training | null = null): TrainingDialogComponent {
    close = jasmine.createSpy('close');
    TestBed.configureTestingModule({
      imports: [TrainingDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MAT_DIALOG_DATA, useValue: training }, { provide: MatDialogRef, useValue: { close } }]
    });
    const fixture = TestBed.createComponent(TrainingDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('records a training, its certification and the funding asked, amounts read with a comma', () => {
    const dialog = create();
    dialog.form.patchValue({ title: ' CKA ', provider: ' ', startsOn: '2026-11-16', endsOn: '2026-11-18', hours: 21, cost: '1800,50',
      certification: 'Certified Kubernetes Administrator', certificationExpiresOn: '2028-11-18', fund: 'FIF_PL',
      fundingDeadline: '2026-11-26', fundedAmount: '900', notes: '' });
    dialog.save();
    expect(close).toHaveBeenCalledWith({ title: 'CKA', provider: null, startsOn: '2026-11-16', endsOn: '2026-11-18', hours: 21,
      cost: 1800.5, certification: 'Certified Kubernetes Administrator', certifiedOn: null, certificationExpiresOn: '2028-11-18',
      fund: 'FIF_PL', fundingStatus: 'TO_REQUEST', fundingDeadline: '2026-11-26', fundedAmount: 900, notes: null });
  });

  it('forgets the funding without a fund and keeps a training to change', () => {
    const dialog = create({ id: 3, title: 'Scrum', fund: null, fundingStatus: 'REQUESTED', fundingDeadline: '2026-11-26', fundingDue: false,
      hours: null, cost: 400 });
    expect(dialog.form.value.cost).toBe('400');
    dialog.save();
    expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ title: 'Scrum', fund: null, fundingStatus: null, fundingDeadline: null,
      fundedAmount: null, hours: null, cost: 400 }));
  });

  it('refuses a training without a title, ending before it starts, or with a wrong amount', () => {
    const dialog = create();
    dialog.save();
    expect(close).not.toHaveBeenCalled();
    dialog.form.patchValue({ title: 'X', startsOn: '2026-11-18', endsOn: '2026-11-16' });
    dialog.save();
    expect(dialog.form.hasError('endsBeforeStart')).toBeTrue();
    dialog.form.patchValue({ endsOn: '2026-11-19', cost: '12.345' });
    dialog.save();
    expect(close).not.toHaveBeenCalled();
  });
});
