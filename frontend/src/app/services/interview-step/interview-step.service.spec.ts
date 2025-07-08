import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { InterviewStepService } from './interview-step.service';
import { 
  InterviewStepDto, 
  CreateInterviewStepDto, 
  UpdateInterviewStepDto, 
  KanbanBoardDto, 
  StepTransitionDto, 
  StepStatus 
} from '../../models/interview-step.models';
import { environment } from '../../../environments/environment';

describe('InterviewStepService', () => {
  let service: InterviewStepService;
  let httpMock: HttpTestingController;
  const API_URL = `${environment.apiUrl}/interview-steps`;

  const mockInterviewStep: InterviewStepDto = {
    id: 1,
    title: 'Technical Interview',
    date: '2024-01-10T14:30:00',
    status: StepStatus.PLANNED,
    notes: 'Technical interview with the team',
    projectId: 1,
    projectRole: 'Full Stack Developer'
  };

  const mockKanbanBoard: KanbanBoardDto = {
    columns: {
      'Prise de Contact': [],
      'Entretien Commercial': [],
      'Positionnement': [],
      'Test Technique': [],
      'Entretien Technique': [],
      'Entretien Manager': [],
      'Validation': []
    },
    stepOrder: [
      'Prise de Contact',
      'Entretien Commercial',
      'Positionnement',
      'Test Technique',
      'Entretien Technique',
      'Entretien Manager',
      'Validation'
    ]
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [InterviewStepService]
    });
    service = TestBed.inject(InterviewStepService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('Basic CRUD operations', () => {
    it('should be created', () => {
      expect(service).toBeTruthy();
    });

    it('should get all interview steps', () => {
      const mockSteps = [mockInterviewStep];

      service.getAll().subscribe(steps => {
        expect(steps).toEqual(mockSteps);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('GET');
      req.flush(mockSteps);
    });

    it('should get interview step by id', () => {
      service.getById(1).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockInterviewStep);
    });

    it('should get interview steps by project id', () => {
      const mockSteps = [mockInterviewStep];

      service.getByProjectId(1).subscribe(steps => {
        expect(steps).toEqual(mockSteps);
      });

      const req = httpMock.expectOne(`${API_URL}/by-project/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockSteps);
    });

    it('should get interview steps by project id ordered by date', () => {
      const mockSteps = [mockInterviewStep];

      service.getByProjectIdOrderByDate(1).subscribe(steps => {
        expect(steps).toEqual(mockSteps);
      });

      const req = httpMock.expectOne(`${API_URL}/by-project/1/ordered`);
      expect(req.request.method).toBe('GET');
      req.flush(mockSteps);
    });

    it('should get interview steps by freelance id and status', () => {
      const mockSteps = [mockInterviewStep];

      service.getByFreelanceIdAndStatus(1, StepStatus.PLANNED).subscribe(steps => {
        expect(steps).toEqual(mockSteps);
      });

      const req = httpMock.expectOne(`${API_URL}/by-freelance/1?status=PLANNED`);
      expect(req.request.method).toBe('GET');
      req.flush(mockSteps);
    });

    it('should create interview step', () => {
      const createDto: CreateInterviewStepDto = {
        title: 'Technical Interview',
        status: StepStatus.TO_PLAN,
        projectId: 1
      };

      service.create(createDto).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(API_URL);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(createDto);
      req.flush(mockInterviewStep);
    });

    it('should update interview step', () => {
      const updateDto: UpdateInterviewStepDto = {
        id: 1,
        title: 'Updated Technical Interview'
      };

      service.update(1, updateDto).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updateDto);
      req.flush(mockInterviewStep);
    });

    it('should delete interview step', () => {
      service.delete(1).subscribe();

      const req = httpMock.expectOne(`${API_URL}/1`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('Status management operations', () => {
    it('should update status', () => {
      service.updateStatus(1, StepStatus.VALIDATED).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1/status?status=VALIDATED`);
      expect(req.request.method).toBe('PATCH');
      req.flush(mockInterviewStep);
    });

    it('should schedule step', () => {
      const date = new Date('2024-02-15T10:00:00');

      service.scheduleStep(1, date).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1/schedule?date=${date.toISOString()}`);
      expect(req.request.method).toBe('PATCH');
      req.flush(mockInterviewStep);
    });

    it('should mark as waiting feedback', () => {
      service.markAsWaitingFeedback(1).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1/waiting-feedback`);
      expect(req.request.method).toBe('PATCH');
      req.flush(mockInterviewStep);
    });

    it('should mark as validated', () => {
      service.markAsValidated(1).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1/validate`);
      expect(req.request.method).toBe('PATCH');
      req.flush(mockInterviewStep);
    });

    it('should mark as failed', () => {
      service.markAsFailed(1).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1/fail`);
      expect(req.request.method).toBe('PATCH');
      req.flush(mockInterviewStep);
    });

    it('should mark as canceled', () => {
      service.markAsCanceled(1).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/1/cancel`);
      expect(req.request.method).toBe('PATCH');
      req.flush(mockInterviewStep);
    });
  });

  describe('Kanban board operations', () => {
    it('should get kanban board', () => {
      service.getKanbanBoard(1).subscribe(board => {
        expect(board).toEqual(mockKanbanBoard);
      });

      const req = httpMock.expectOne(`${API_URL}/kanban/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockKanbanBoard);
    });

    it('should transition project to next step', () => {
      const transition: StepTransitionDto = {
        projectId: 1,
        fromStepTitle: 'Technical Interview',
        toStepTitle: 'Manager Interview',
        notes: 'Moving to next step'
      };

      service.transitionProjectToNextStep(transition).subscribe(step => {
        expect(step).toEqual(mockInterviewStep);
      });

      const req = httpMock.expectOne(`${API_URL}/transition`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(transition);
      req.flush(mockInterviewStep);
    });
  });

  describe('Utility methods', () => {
    it('should get status label', () => {
      expect(service.getStatusLabel(StepStatus.TO_PLAN)).toBe('À planifier');
      expect(service.getStatusLabel(StepStatus.PLANNED)).toBe('Planifié');
      expect(service.getStatusLabel(StepStatus.VALIDATED)).toBe('Validé');
      expect(service.getStatusLabel(StepStatus.FAILED)).toBe('Échoué');
      expect(service.getStatusLabel(StepStatus.CANCELED)).toBe('Annulé');
      expect(service.getStatusLabel(StepStatus.WAITING_FEEDBACK)).toBe('En attente de retour');
    });

    it('should get status color', () => {
      expect(service.getStatusColor(StepStatus.TO_PLAN)).toBe('#ffa726');
      expect(service.getStatusColor(StepStatus.PLANNED)).toBe('#42a5f5');
      expect(service.getStatusColor(StepStatus.VALIDATED)).toBe('#66bb6a');
      expect(service.getStatusColor(StepStatus.FAILED)).toBe('#ef5350');
      expect(service.getStatusColor(StepStatus.CANCELED)).toBe('#bdbdbd');
      expect(service.getStatusColor(StepStatus.WAITING_FEEDBACK)).toBe('#ffee58');
    });

    it('should check if step is completed', () => {
      expect(service.isStepCompleted(StepStatus.VALIDATED)).toBe(true);
      expect(service.isStepCompleted(StepStatus.PLANNED)).toBe(false);
      expect(service.isStepCompleted(StepStatus.FAILED)).toBe(false);
    });

    it('should check if step is failed', () => {
      expect(service.isStepFailed(StepStatus.FAILED)).toBe(true);
      expect(service.isStepFailed(StepStatus.VALIDATED)).toBe(false);
      expect(service.isStepFailed(StepStatus.PLANNED)).toBe(false);
    });

    it('should check if step is active', () => {
      expect(service.isStepActive(StepStatus.PLANNED)).toBe(true);
      expect(service.isStepActive(StepStatus.WAITING_FEEDBACK)).toBe(true);
      expect(service.isStepActive(StepStatus.VALIDATED)).toBe(false);
      expect(service.isStepActive(StepStatus.TO_PLAN)).toBe(false);
    });

    it('should check if can transition to next', () => {
      expect(service.canTransitionToNext(StepStatus.VALIDATED)).toBe(true);
      expect(service.canTransitionToNext(StepStatus.WAITING_FEEDBACK)).toBe(true);
      expect(service.canTransitionToNext(StepStatus.PLANNED)).toBe(false);
      expect(service.canTransitionToNext(StepStatus.TO_PLAN)).toBe(false);
    });
  });
});
