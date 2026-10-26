import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { InterviewStepDto, StepStatus } from '../../../models';
import { InterviewStepService } from '../../../services/interview-step/interview-step.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { StepChecklistComponent } from './step-checklist.component';
import en from '../../../../assets/i18n/en.json';

describe('StepChecklistComponent', () => {
  let service: jasmine.SpyObj<InterviewStepService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const interview: InterviewStepDto = {
    id: 3, title: 'Technical interview', status: StepStatus.PLANNED, projectId: 9,
    checklist: [{ label: 'Reread the offer', done: true }, { label: 'Test the call', done: false }]
  };

  function create(step: InterviewStepDto = interview): ComponentFixture<StepChecklistComponent> {
    service = jasmine.createSpyObj('InterviewStepService', ['update']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [StepChecklistComponent, TranslateModule.forRoot()],
      providers: [
        { provide: InterviewStepService, useValue: service },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(StepChecklistComponent);
    fixture.componentRef.setInput('step', step);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<StepChecklistComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  function checkboxes(fixture: ComponentFixture<StepChecklistComponent>): HTMLInputElement[] {
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('input[type="checkbox"]'));
  }

  it('shows how much is ready and each item', () => {
    const fixture = create();

    expect(text(fixture)).toContain('Preparation · 1/2 ready');
    expect(checkboxes(fixture).map(box => box.checked)).toEqual([true, false]);
    expect(text(fixture)).toContain('Test the call');
  });

  it('shows nothing for a step without checklist', () => {
    const fixture = create({ ...interview, checklist: undefined });

    expect(text(fixture).trim()).toBe('');
  });

  it('ticks an item at once and saves the step with it', () => {
    const fixture = create();
    const answer = new Subject<InterviewStepDto>();
    service.update.and.returnValue(answer);
    const emitted: InterviewStepDto[] = [];
    fixture.componentInstance.saved.subscribe(step => emitted.push(step));

    checkboxes(fixture)[1].click();

    expect(text(fixture)).toContain('2/2 ready');
    expect(service.update).toHaveBeenCalledOnceWith(3, jasmine.objectContaining({
      id: 3, title: 'Technical interview', checklist: [{ label: 'Reread the offer', done: true }, { label: 'Test the call', done: true }]
    }));
    const saved = { ...interview, checklist: [{ label: 'Reread the offer', done: true }, { label: 'Test the call', done: true }] };
    answer.next(saved);
    expect(emitted).toEqual([saved]);
  });

  it('puts the item back and says so when the save fails', () => {
    const fixture = create();
    service.update.and.returnValue(throwError(() => new Error('down')));

    checkboxes(fixture)[0].click();

    expect(text(fixture)).toContain('1/2 ready');
    expect(fixture.componentInstance.step).toBe(interview);
    expect(notifications.error).toHaveBeenCalledWith('steps.checklist.saveError');
  });

  it('saves nothing for a step not saved yet', () => {
    const fixture = create({ ...interview, id: undefined });
    service.update.and.returnValue(of(interview));

    fixture.componentInstance.toggle(0);

    expect(service.update).not.toHaveBeenCalled();
  });
});
