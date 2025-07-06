import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ProjectService, ProjectDto } from './project.service';
import { environment } from '../../environments/environment';

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
    it('should fetch project by id', (done) => {
      const projectId = 1;

      service.getById(projectId).subscribe(project => {
        expect(project).toBeDefined();
        expect(project?.id).toBe(1);
        expect(project?.role).toBe('Développeur Full Stack');
        expect(project?.clientName).toBe('TechCorp');
        expect(project?.workMode).toBe('HYBRID');
        done();
      });
    });

    it('should return error for non-existent project', (done) => {
      const projectId = 999;

      service.getById(projectId).subscribe({
        next: () => {
          fail('Expected error but got success');
        },
        error: (error) => {
          expect(error.message).toBe('Project not found');
          done();
        }
      });
    });
  });

  describe('getByFreelanceId', () => {
    it('should fetch projects by freelance id', (done) => {
      const freelanceId = 1;

      service.getByFreelanceId(freelanceId).subscribe(projects => {
        expect(projects).toBeDefined();
        expect(projects.length).toBe(3);
        expect(projects[0].id).toBe(1);
        expect(projects[0].role).toBe('Développeur Full Stack');
        expect(projects[1].id).toBe(2);
        expect(projects[2].id).toBe(3);
        done();
      });
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
