import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProjectService } from './project.service';
import { ProjectDto } from '../../models';
import { environment } from '../../../environments/environment';

describe('ProjectService', () => {
  let service: ProjectService;
  let httpMock: HttpTestingController;

  const mockProject: ProjectDto = {
    id: 1,
    role: 'Full Stack Developer',
    description: 'Test project description',
    techStack: 'Angular, Spring Boot',
    dailyRate: 600,
    workMode: 'REMOTE',
    remoteDaysPerMonth: 20,
    onsiteDaysPerMonth: 0,
    startDate: '2024-01-15',
    durationInMonths: 6,
    daysPerYear: 220,
    personalRating: 4,
    freelanceId: 1,
    clientId: 1,
    sourceId: 1,
    clientName: 'Test Client',
    sourceName: 'LinkedIn'
  };

  const mockProjects: ProjectDto[] = [
    mockProject,
    {
      ...mockProject,
      id: 2,
      role: 'Backend Developer',
      workMode: 'HYBRID',
      dailyRate: 550
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ProjectService,
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });

    service = TestBed.inject(ProjectService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAll', () => {
    it('should fetch all projects', () => {
      service.getAll().subscribe(projects => {
        expect(projects).toEqual(mockProjects);
        expect(projects.length).toBe(2);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProjects);
    });

    it('should handle error when fetching all projects', () => {
      service.getAll().subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(500);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects`);
      req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    });
  });

  describe('getById', () => {
    it('should fetch project by id', () => {
      const projectId = 1;

      service.getById(projectId).subscribe(project => {
        expect(project).toEqual(mockProject);
        expect(project.id).toBe(projectId);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/${projectId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProject);
    });

    it('should handle error when project not found', () => {
      const projectId = 999;

      service.getById(projectId).subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/${projectId}`);
      req.flush('Project not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('getByFreelanceId', () => {
    it('should fetch projects by freelance id', () => {
      const freelanceId = 1;

      service.getByFreelanceId(freelanceId).subscribe(projects => {
        expect(projects).toEqual(mockProjects);
        expect(projects.length).toBe(2);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/by-freelance/${freelanceId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProjects);
    });

    it('should handle error when fetching projects by freelance id', () => {
      const freelanceId = 999;

      service.getByFreelanceId(freelanceId).subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/by-freelance/${freelanceId}`);
      req.flush('Freelance not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('getByIdWithSteps', () => {
    it('should fetch project by id with steps', () => {
      const projectId = 1;
      const projectWithSteps = { ...mockProject, steps: [] };

      service.getByIdWithSteps(projectId).subscribe(project => {
        expect(project).toEqual(projectWithSteps);
        expect(project.id).toBe(projectId);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/${projectId}/with-steps`);
      expect(req.request.method).toBe('GET');
      req.flush(projectWithSteps);
    });
  });

  describe('getByClientId', () => {
    it('should fetch projects by client id', () => {
      const clientId = 1;

      service.getByClientId(clientId).subscribe(projects => {
        expect(projects).toEqual(mockProjects);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/by-client/${clientId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockProjects);
    });
  });

  describe('getByFreelanceIdWithFilters', () => {
    it('should fetch filtered projects by freelance id', () => {
      const freelanceId = 1;
      const filters = {
        minRate: 500,
        maxRate: 700,
        workMode: 'REMOTE' as const,
        startDateAfter: '2024-01-01',
        techStack: 'Angular'
      };

      service.getByFreelanceIdWithFilters(freelanceId, filters).subscribe(projects => {
        expect(projects).toEqual(mockProjects);
      });

      const req = httpMock.expectOne(req =>
        req.url === `${environment.apiUrl}/projects/by-freelance/${freelanceId}/filtered` &&
        req.params.get('minRate') === '500' &&
        req.params.get('maxRate') === '700' &&
        req.params.get('workMode') === 'REMOTE' &&
        req.params.get('startDateAfter') === '2024-01-01' &&
        req.params.get('techStack') === 'Angular'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockProjects);
    });

    it('should fetch projects with partial filters', () => {
      const freelanceId = 1;
      const filters = { minRate: 500 };

      service.getByFreelanceIdWithFilters(freelanceId, filters).subscribe(projects => {
        expect(projects).toEqual(mockProjects);
      });

      const req = httpMock.expectOne(req =>
        req.url === `${environment.apiUrl}/projects/by-freelance/${freelanceId}/filtered` &&
        req.params.get('minRate') === '500' &&
        !req.params.has('maxRate')
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockProjects);
    });
  });

  describe('create', () => {
    it('should create a new project', () => {
      const newProject = { ...mockProject };
      delete newProject.id;

      service.create(newProject).subscribe(project => {
        expect(project).toEqual(mockProject);
        expect(project.id).toBeDefined();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newProject);
      req.flush(mockProject);
    });

    it('should handle validation error when creating project', () => {
      const invalidProject = { ...mockProject, role: '' };

      service.create(invalidProject).subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects`);
      req.flush('Validation Error', { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('update', () => {
    it('should update an existing project', () => {
      const updatedProject = { ...mockProject, role: 'Senior Full Stack Developer' };

      service.update(mockProject.id!, updatedProject).subscribe(project => {
        expect(project).toEqual(updatedProject);
        expect(project.role).toBe('Senior Full Stack Developer');
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/${mockProject.id}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedProject);
      req.flush(updatedProject);
    });
  });

  describe('delete', () => {
    it('should delete a project', () => {
      const projectId = 1;

      service.delete(projectId).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/${projectId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('getAverageDailyRate', () => {
    it('should fetch average daily rate for freelance', () => {
      const freelanceId = 1;
      const averageRate = 575;

      service.getAverageDailyRate(freelanceId).subscribe(rate => {
        expect(rate).toBe(averageRate);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/stats/average-rate/${freelanceId}`);
      expect(req.request.method).toBe('GET');
      req.flush(averageRate);
    });
  });

  describe('getProjectCount', () => {
    it('should fetch project count for freelance', () => {
      const freelanceId = 1;
      const projectCount = 5;

      service.getProjectCount(freelanceId).subscribe(count => {
        expect(count).toBe(projectCount);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/projects/stats/count/${freelanceId}`);
      expect(req.request.method).toBe('GET');
      req.flush(projectCount);
    });
  });


});
