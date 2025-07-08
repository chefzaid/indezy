import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { of, throwError } from 'rxjs';

import { StepActionDialogComponent, StepActionDialogData } from './step-action-dialog.component';
import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { ProjectCardDto, StepStatus } from '../../models/interview-step.models';

describe('StepActionDialogComponent', () => {
  let component: StepActionDialogComponent;
  let fixture: ComponentFixture<StepActionDialogComponent>;
  let mockDialogRef: jasmine.SpyObj<MatDialogRef<StepActionDialogComponent>>;
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

  const mockDialogData: StepActionDialogData = {
    projectCard: mockProjectCard,
    stepId: 1,
    action: 'validated',
    title: 'Valider l\'étape',
    message: 'Cette étape sera marquée comme validée.',
    confirmButtonText: 'Valider',
    confirmButtonColor: 'primary'
  };

  const mockUpdatedStep = {
    id: 1,
    title: 'Test Technique',
    date: '2024-01-10T14:30:00',
    status: StepStatus.VALIDATED,
    notes: 'Step validated',
    projectId: 1,
    projectRole: 'Full Stack Developer'
  };

  beforeEach(async () => {
    const dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);
    const interviewStepServiceSpy = jasmine.createSpyObj('InterviewStepService', [
      'markAsWaitingFeedback',
      'markAsValidated',
      'markAsFailed',
      'markAsCanceled'
    ]);
    const snackBarSpy = jasmine.createSpyObj('MatSnackBar', ['open']);

    await TestBed.configureTestingModule({
      imports: [
        StepActionDialogComponent,
        ReactiveFormsModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: MatDialogRef, useValue: dialogRefSpy },
        { provide: MAT_DIALOG_DATA, useValue: mockDialogData },
        { provide: InterviewStepService, useValue: interviewStepServiceSpy },
        { provide: MatSnackBar, useValue: snackBarSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StepActionDialogComponent);
    component = fixture.componentInstance;
    mockDialogRef = TestBed.inject(MatDialogRef) as jasmine.SpyObj<MatDialogRef<StepActionDialogComponent>>;
    mockInterviewStepService = TestBed.inject(InterviewStepService) as jasmine.SpyObj<InterviewStepService>;
    mockSnackBar = TestBed.inject(MatSnackBar) as jasmine.SpyObj<MatSnackBar>;

    // Set up default return values for all service methods
    mockInterviewStepService.markAsValidated.and.returnValue(of(mockUpdatedStep));
    mockInterviewStepService.markAsWaitingFeedback.and.returnValue(of(mockUpdatedStep));
    mockInterviewStepService.markAsFailed.and.returnValue(of(mockUpdatedStep));
    mockInterviewStepService.markAsCanceled.and.returnValue(of(mockUpdatedStep));

    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Form initialization', () => {
    it('should initialize form with empty notes', () => {
      expect(component.actionForm.get('notes')?.value).toBe('');
    });
  });

  describe('Action confirmation', () => {
    it('should call markAsValidated for validated action', fakeAsync(() => {
      // Ensure the action is set correctly for this test
      component.data.action = 'validated';
      mockInterviewStepService.markAsValidated.and.returnValue(of(mockUpdatedStep));

      component.onConfirm();
      tick(); // Process the observable

      expect(mockInterviewStepService.markAsValidated).toHaveBeenCalledWith(1);
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Étape validée avec succès',
        'Fermer',
        { duration: 3000 }
      );
      expect(mockDialogRef.close).toHaveBeenCalledWith(mockUpdatedStep);
    }));

    it('should call markAsWaitingFeedback for waiting_feedback action', () => {
      component.data.action = 'waiting_feedback';
      mockInterviewStepService.markAsWaitingFeedback.and.returnValue(of(mockUpdatedStep));

      component.onConfirm();

      expect(mockInterviewStepService.markAsWaitingFeedback).toHaveBeenCalledWith(1);
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Étape marquée en attente de retour',
        'Fermer',
        { duration: 3000 }
      );
    });

    it('should call markAsFailed for failed action', () => {
      component.data.action = 'failed';
      mockInterviewStepService.markAsFailed.and.returnValue(of(mockUpdatedStep));

      component.onConfirm();

      expect(mockInterviewStepService.markAsFailed).toHaveBeenCalledWith(1);
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Étape marquée comme échouée',
        'Fermer',
        { duration: 3000 }
      );
    });

    it('should call markAsCanceled for canceled action', () => {
      component.data.action = 'canceled';
      mockInterviewStepService.markAsCanceled.and.returnValue(of(mockUpdatedStep));

      component.onConfirm();

      expect(mockInterviewStepService.markAsCanceled).toHaveBeenCalledWith(1);
      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Étape annulée',
        'Fermer',
        { duration: 3000 }
      );
    });

    it('should handle unknown action', () => {
      component.data.action = 'unknown' as any;

      component.onConfirm();

      expect(mockInterviewStepService.markAsValidated).not.toHaveBeenCalled();
      expect(mockInterviewStepService.markAsWaitingFeedback).not.toHaveBeenCalled();
      expect(mockInterviewStepService.markAsFailed).not.toHaveBeenCalled();
      expect(mockInterviewStepService.markAsCanceled).not.toHaveBeenCalled();
      expect(component.isSubmitting).toBe(false);
    });

    it('should handle action error', fakeAsync(() => {
      // Ensure the action is set correctly for this test
      component.data.action = 'validated';
      mockInterviewStepService.markAsValidated.and.returnValue(
        throwError(() => new Error('Action failed'))
      );

      component.onConfirm();
      tick(); // Process the observable

      expect(mockSnackBar.open).toHaveBeenCalledWith(
        'Erreur lors de la mise à jour',
        'Fermer',
        { duration: 3000 }
      );
      expect(component.isSubmitting).toBe(false);
      expect(mockDialogRef.close).not.toHaveBeenCalled();
    }));

    it('should not confirm if already submitting', () => {
      component.isSubmitting = true;

      component.onConfirm();

      expect(mockInterviewStepService.markAsValidated).not.toHaveBeenCalled();
    });
  });

  describe('Dialog actions', () => {
    it('should close dialog on cancel', () => {
      component.onCancel();

      expect(mockDialogRef.close).toHaveBeenCalledWith();
    });
  });

  describe('UI helper methods', () => {
    it('should return correct icon for each action', () => {
      // Reset to original action and test validated
      component.data.action = 'validated';
      expect(component.getActionIcon()).toBe('check_circle');

      component.data.action = 'waiting_feedback';
      expect(component.getActionIcon()).toBe('hourglass_empty');

      component.data.action = 'failed';
      expect(component.getActionIcon()).toBe('cancel');

      component.data.action = 'canceled';
      expect(component.getActionIcon()).toBe('block');
    });

    it('should return correct color for each action', () => {
      // Reset to original action and test validated
      component.data.action = 'validated';
      expect(component.getActionColor()).toBe('#66bb6a');

      component.data.action = 'waiting_feedback';
      expect(component.getActionColor()).toBe('#ffee58');

      component.data.action = 'failed';
      expect(component.getActionColor()).toBe('#ef5350');

      component.data.action = 'canceled';
      expect(component.getActionColor()).toBe('#ef5350');
    });

    it('should return correct success message for each action', () => {
      // Reset to original action
      component.data.action = 'validated';
      expect(component['getSuccessMessage']()).toBe('Étape validée avec succès');

      component.data.action = 'waiting_feedback';
      expect(component['getSuccessMessage']()).toBe('Étape marquée en attente de retour');

      component.data.action = 'failed';
      expect(component['getSuccessMessage']()).toBe('Étape marquée comme échouée');

      component.data.action = 'canceled';
      expect(component['getSuccessMessage']()).toBe('Étape annulée');

      component.data.action = 'unknown' as any;
      expect(component['getSuccessMessage']()).toBe('Statut mis à jour');
    });
  });
});
