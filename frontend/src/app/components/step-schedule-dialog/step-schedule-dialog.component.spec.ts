import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule, FormBuilder } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { StepScheduleDialogComponent, StepScheduleDialogData } from './step-schedule-dialog.component';
import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { ProjectCardDto, StepStatus } from '../../models/interview-step.models';

describe('StepScheduleDialogComponent', () => {
  let component: StepScheduleDialogComponent;
  let fixture: ComponentFixture<StepScheduleDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<StepScheduleDialogComponent>>;
  let mockInterviewStepService: jasmine.SpyObj<InterviewStepService>;
  let mockSnackBar: jasmine.SpyObj<MatSnackBar>;

  const mockProjectCard: ProjectCardDto = {
    projectId: 1,
    role: 'Full Stack Developer',
    clientName: 'Test Company',
    dailyRate: 600,
    workMode: 'HYBRID',
    techStack: 'Angular, Spring Boot',
    currentStepTitle: 'Test Technique',
    currentStepStatus: 'PLANNED',
    currentStepDate: '2024-01-10T14:30:00',
    notes: 'Technical interview notes',
    totalSteps: 7,
    completedSteps: 2,
    failedSteps: 0
  };

  const mockDialogData: StepScheduleDialogData = {
    projectCard: mockProjectCard,
    stepId: 1
  };

  const mockUpdatedStep = {
    id: 1,
    title: 'Test Technique',
    date: '2024-02-15T10:00:00',
    status: StepStatus.PLANNED,
    notes: 'Scheduled interview',
    projectId: 1,
    projectRole: 'Full Stack Developer'
  };

  beforeEach(async () => {
    const dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    const interviewStepServiceSpy = jasmine.createSpyObj('InterviewStepService', ['scheduleStep']);
    const snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [
        StepScheduleDialogComponent,
        ReactiveFormsModule,
        NoopAnimationsModule
      ],
      providers: [
        FormBuilder,
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: mockDialogData },
        { provide: InterviewStepService, useValue: interviewStepServiceSpy },
        { provide: MatSnackBar, useValue: snackBarSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StepScheduleDialogComponent);
    component = fixture.componentInstance;
    mockDialogRef = TestBed.inject(MatDialogRef) as jasmine.SpyObj<MatDialogRef<StepScheduleDialogComponent>>;
    mockInterviewStepService = TestBed.inject(InterviewStepService) as jasmine.SpyObj<InterviewStepService>;
    mockSnackBar = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Form initialization', () => {
    it('should initialize form with empty values when no existing date', async () => {
      // Create a new test bed with data that has no current step date
      const dialogDataWithoutDate = { ...mockDialogData, projectCard: { ...mockProjectCard, currentStepDate: undefined } };

      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [
          StepScheduleDialogComponent,
          ReactiveFormsModule,
          NoopAnimationsModule
        ],
        providers: [
          FormBuilder,
          { provide: MatDialogRef, useValue: mockDialogRef },
          { provide: MAT_DIALOG_DATA, useValue: dialogDataWithoutDate },
          { provide: InterviewStepService, useValue: mockInterviewStepService },
          { provide: MatSnackBar, useValue: mockSnackBar }
        ]
      }).compileComponents();

      const fixtureWithoutDate = TestBed.createComponent(StepScheduleDialogComponent);
      const componentWithoutDate = fixtureWithoutDate.componentInstance;
      fixtureWithoutDate.detectChanges();

      expect(componentWithoutDate.scheduleForm.get('date')?.value).toBeNull();
      expect(componentWithoutDate.scheduleForm.get('time')?.value).toBe('');
      expect(componentWithoutDate.scheduleForm.get('notes')?.value).toBe('');
    });

    it('should pre-fill form with existing date when available', () => {
      expect(component.scheduleForm.get('date')?.value).toEqual(new Date('2024-01-10T14:30:00'));
      expect(component.scheduleForm.get('time')?.value).toBe('14:30');
    });

    it('should have required validators on date and time fields', () => {
      const dateControl = component.scheduleForm.get('date');
      const timeControl = component.scheduleForm.get('time');

      dateControl?.setValue(null);
      timeControl?.setValue('');

      expect(dateControl?.hasError('required')).toBe(true);
      expect(timeControl?.hasError('required')).toBe(true);
    });
  });

  describe('Form submission', () => {
    beforeEach(() => {
      // Use a future date to pass the dateFilter
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 7); // 7 days from now

      component.scheduleForm.patchValue({
        date: futureDate,
        time: '10:00',
        notes: 'Scheduled interview'
      });
    });

    it('should submit form with valid data', () => {
      mockInterviewStepService.scheduleStep.and.returnValue(of(mockUpdatedStep));

      // Ensure form is valid
      expect(component.scheduleForm.valid).toBe(true);

      component.onSubmit();

      expect(mockInterviewStepService.scheduleStep).toHaveBeenCalledWith(
        1,
        jasmine.any(Date)
      );
    });

    it('should show success message and close dialog on successful submission', fakeAsync(() => {
      mockInterviewStepService.scheduleStep.and.returnValue(of(mockUpdatedStep));

      component.onSubmit();
      tick(); // Process the observable

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Entretien planifié avec succès',
        'Fermer',
        { duration: 3000 }
      );
      expect(mockDialogRef.close).toHaveBeenCalledWith(mockUpdatedStep);
    }));

    it('should handle submission error', fakeAsync(() => {
      mockInterviewStepService.scheduleStep.and.returnValue(
        throwError(() => new Error('Scheduling failed'))
      );

      component.onSubmit();
      tick(); // Process the observable

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Erreur lors de la planification',
        'Fermer',
        { duration: 3000 }
      );
      expect(component.isSubmitting).toBe(false);
      expect(mockDialogRef.close).not.toHaveBeenCalled();
    }));

    it('should not submit if form is invalid', () => {
      component.scheduleForm.patchValue({
        date: null,
        time: ''
      });

      component.onSubmit();

      expect(mockInterviewStepService.scheduleStep).not.toHaveBeenCalled();
    });

    it('should not submit if already submitting', () => {
      component.isSubmitting = true;

      component.onSubmit();

      expect(mockInterviewStepService.scheduleStep).not.toHaveBeenCalled();
    });
  });

  describe('Dialog actions', () => {
    it('should close dialog on cancel', () => {
      component.onCancel();

      expect(mockDialogRef.close).toHaveBeenCalledWith();
    });
  });

  describe('Date filtering', () => {
    it('should allow future dates', () => {
      const futureDate = new Date();
      futureDate.setDate(futureDate.getDate() + 1);

      const result = component.dateFilter(futureDate);

      expect(result).toBe(true);
    });

    it('should allow today', () => {
      const today = new Date();

      const result = component.dateFilter(today);

      expect(result).toBe(true);
    });

    it('should not allow past dates', () => {
      const pastDate = new Date();
      pastDate.setDate(pastDate.getDate() - 1);

      const result = component.dateFilter(pastDate);

      expect(result).toBe(false);
    });

    it('should not allow null dates', () => {
      const result = component.dateFilter(null);

      expect(result).toBe(false);
    });
  });

  describe('Utility methods', () => {
    it('should combine date and time correctly', () => {
      const date = new Date('2024-02-15');
      const time = '10:30';

      const result = component['combineDateTime'](date, time);

      expect(result.getFullYear()).toBe(2024);
      expect(result.getMonth()).toBe(1); // February is month 1
      expect(result.getDate()).toBe(15);
      expect(result.getHours()).toBe(10);
      expect(result.getMinutes()).toBe(30);
    });

    it('should format time for input correctly', () => {
      const date = new Date('2024-02-15T14:30:00');

      const result = component['formatTimeForInput'](date);

      expect(result).toBe('14:30');
    });
  });
});
