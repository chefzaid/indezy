import { InterviewStepDialogComponent } from './interview-step-dialog.component';
import { FormBuilder } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { of, Subject, throwError } from 'rxjs';
import { InterviewStepDto, StepStatus } from '../../../models';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { TranslateService } from '@ngx-translate/core';

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

describe('InterviewStepDialogComponent submissions', () => {
  let service: jasmine.SpyObj<InterviewStepService>;
  let dialog: jasmine.SpyObj<MatDialogRef<InterviewStepDialogComponent, InterviewStepDto>>;
  let notification: jasmine.SpyObj<NotificationService>;
  const saved: InterviewStepDto = { id: 4, title: 'Interview', status: StepStatus.PLANNED, projectId: 9 };

  beforeEach(() => {
    service = jasmine.createSpyObj('InterviewStepService', ['create', 'update']);
    dialog = jasmine.createSpyObj('MatDialogRef', ['close']);
    notification = jasmine.createSpyObj('NotificationService', ['success', 'error']);
  });

  const translate = { instant: (key: string) => `T:${key.split('.').pop()}` } as unknown as TranslateService;

  function create(step?: InterviewStepDto): InterviewStepDialogComponent {
    return new InterviewStepDialogComponent(new FormBuilder(), dialog, { projectId: 9, step }, service, notification, translate);
  }

  it('plans a new step when a date is chosen and saves the selected wall-clock time', () => {
    service.create.and.returnValue(of(saved));
    const component = create();
    component.stepForm.patchValue({ title: ' Interview ', date: new Date(2026, 8, 15), time: '14:30', notes: ' Prepare ' });
    expect(component.stepForm.value.status).toBe(StepStatus.PLANNED);
    component.onSubmit();
    expect(service.create).toHaveBeenCalledOnceWith({
      title: 'Interview', date: '2026-09-15T14:30:00', notes: 'Prepare', status: StepStatus.PLANNED, projectId: 9, checklist: []
    });
    expect(dialog.close).toHaveBeenCalledOnceWith(saved);
    expect(notification.success).toHaveBeenCalledWith('steps.addedSuccess');
  });

  it('edits a completed step without resetting its status when the date changes', () => {
    service.update.and.returnValue(of(saved));
    const component = create({ ...saved, date: '2026-09-15T14:30:00', status: StepStatus.VALIDATED });
    expect(component.stepForm.value.time).toBe('14:30');
    component.stepForm.patchValue({ date: new Date(2026, 8, 16) });
    component.onSubmit();
    expect(service.update).toHaveBeenCalledOnceWith(4, {
      id: 4, title: 'Interview', date: '2026-09-16T14:30:00', notes: undefined, status: StepStatus.VALIDATED, projectId: 9,
      checklist: []
    });
    expect(notification.success).toHaveBeenCalledWith('steps.updatedSuccess');
  });

  it('blocks invalid and repeated requests', () => {
    const component = create();
    component.onSubmit();
    expect(service.create).not.toHaveBeenCalled();
    expect(component.stepForm.get('title')!.touched).toBeTrue();

    const pending = new Subject<InterviewStepDto>();
    service.create.and.returnValue(pending);
    component.stepForm.patchValue({ title: 'Interview' });
    component.onSubmit();
    component.onSubmit();
    expect(service.create).toHaveBeenCalledTimes(1);
    pending.next(saved);
    pending.complete();
  });

  it('allows retrying a failed save and canceling without a result', () => {
    service.create.and.returnValue(throwError(() => new Error('offline')));
    const component = create();
    component.stepForm.patchValue({ title: 'Interview' });
    component.onSubmit();
    expect(component.isSubmitting).toBeFalse();
    expect(notification.error).toHaveBeenCalledWith('errors.updatingStep');
    expect(dialog.close).not.toHaveBeenCalled();
    component.onCancel();
    expect(dialog.close).toHaveBeenCalledWith();
  });

  describe('preparation checklist', () => {
    it('keeps the step\'s checklist and saves it edited, in its order', () => {
      service.update.and.returnValue(of(saved));
      const component = create({ ...saved, checklist: [{ label: 'Reread the offer', done: true }, { label: 'Test the call', done: false }] });

      component.newItem.setValue('  Prepare questions ');
      component.addItem();
      component.removeItem(0);
      component.checklist.at(0).patchValue({ done: true });
      component.onSubmit();

      expect(component.newItem.value).toBe('');
      expect(service.update.calls.mostRecent().args[1].checklist).toEqual([
        { label: 'Test the call', done: true }, { label: 'Prepare questions', done: false }
      ]);
    });

    it('adds neither an empty item nor one already listed, whatever its case', () => {
      const component = create({ ...saved, checklist: [{ label: 'Test the call', done: false }] });

      component.addItem('   ');
      component.addItem(' test THE call ');

      expect(component.checklist.length).toBe(1);
    });

    it('cuts an item at the length the API keeps', () => {
      const component = create();

      component.addItem('x'.repeat(250));

      expect(component.checklist.at(0).value.label.length).toBe(200);
    });

    it('adds the suggested items once, in the interface language', () => {
      const component = create({ ...saved, checklist: [{ label: 'T:company', done: true }] });
      expect(component.suggestionsListed).toBeFalse();

      component.addSuggestions();
      component.addSuggestions();

      expect(component.checklist.value.map((item: { label: string }) => item.label)).toEqual([
        'T:company', 'T:jobDescription', 'T:examples', 'T:questions', 'T:rate', 'T:connection'
      ]);
      expect(component.checklist.at(0).value.done).toBeTrue();
      expect(component.suggestionsListed).toBeTrue();
    });
  });
});
