import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { FreelanceService } from './freelance.service';
import { FreelanceDto } from '../../models';
import { environment } from '../../../environments/environment';

describe('FreelanceService', () => {
  let service: FreelanceService;
  let httpMock: HttpTestingController;

  const mockFreelance: FreelanceDto = {
    id: 1,
    firstName: 'John',
    lastName: 'Doe',
    email: 'john.doe@email.com',
    phone: '+33 6 12 34 56 78',
    birthDate: '1990-01-15',
    address: '123 Rue de la Paix',
    city: 'Paris',
    status: 'AVAILABLE',
    noticePeriodInDays: 30,
    availabilityDate: '2024-02-01',
    reversionRate: 0.15,
    cvFilePath: '/path/to/cv.pdf',
    fullName: 'John Doe',
    totalProjects: 5,
    averageDailyRate: 650
  };

  const mockFreelances: FreelanceDto[] = [
    mockFreelance,
    {
      ...mockFreelance,
      id: 2,
      firstName: 'Jane',
      lastName: 'Smith',
      email: 'jane.smith@email.com',
      status: 'EMPLOYED'
    }
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [FreelanceService]
    });

    service = TestBed.inject(FreelanceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('getAll', () => {
    it('should fetch all freelances', () => {
      service.getAll().subscribe(freelances => {
        expect(freelances).toEqual(mockFreelances);
        expect(freelances.length).toBe(2);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances`);
      expect(req.request.method).toBe('GET');
      req.flush(mockFreelances);
    });

    it('should handle error when fetching all freelances', () => {
      service.getAll().subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(500);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances`);
      req.flush('Server Error', { status: 500, statusText: 'Internal Server Error' });
    });
  });

  describe('getById', () => {
    it('should fetch freelance by id', () => {
      const freelanceId = 1;

      service.getById(freelanceId).subscribe(freelance => {
        expect(freelance).toEqual(mockFreelance);
        expect(freelance.id).toBe(freelanceId);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${freelanceId}`);
      expect(req.request.method).toBe('GET');
      req.flush(mockFreelance);
    });

    it('should handle 404 error when freelance not found', () => {
      const freelanceId = 999;

      service.getById(freelanceId).subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${freelanceId}`);
      req.flush('Not Found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('getByIdWithProjects', () => {
    it('should return mock freelance data with projects', () => {
      const freelanceId = 1;

      service.getByIdWithProjects(freelanceId).subscribe(freelance => {
        expect(freelance).toBeDefined();
        expect(freelance.id).toBe(freelanceId);
        expect(freelance.firstName).toBe('John');
        expect(freelance.lastName).toBe('Doe');
        expect(freelance.email).toBe('john.doe@email.com');
        expect(freelance.status).toBe('AVAILABLE');
        expect(freelance.totalProjects).toBe(5);
        expect(freelance.averageDailyRate).toBe(650);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${freelanceId}/with-projects`);
      expect(req.request.method).toBe('GET');
      req.flush(mockFreelances[0]);
    });

    it('should simulate API delay', () => {
      const freelanceId = 1;

      service.getByIdWithProjects(freelanceId).subscribe(freelance => {
        expect(freelance).toBeDefined();
        expect(freelance.id).toBe(freelanceId);
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${freelanceId}/with-projects`);
      expect(req.request.method).toBe('GET');
      req.flush(mockFreelances[0]);
    });
  });

  describe('getByEmail', () => {
    it('should fetch freelance by email', () => {
      const email = 'john.doe@email.com';

      service.getByEmail(email).subscribe(freelance => {
        expect(freelance).toEqual(mockFreelance);
        expect(freelance.email).toBe(email);
      });

      const req = httpMock.expectOne((request) => {
        return request.url === `${environment.apiUrl}/freelances/by-email` &&
               request.params.get('email') === email;
      });
      expect(req.request.method).toBe('GET');
      req.flush(mockFreelance);
    });
  });

  describe('create', () => {
    it('should create a new freelance', () => {
      const newFreelance = { ...mockFreelance };
      delete newFreelance.id;

      service.create(newFreelance).subscribe(freelance => {
        expect(freelance).toEqual(mockFreelance);
        expect(freelance.id).toBeDefined();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(newFreelance);
      req.flush(mockFreelance);
    });

    it('should handle validation error when creating freelance', () => {
      const invalidFreelance = { ...mockFreelance, email: 'invalid-email' };

      service.create(invalidFreelance).subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances`);
      req.flush('Validation Error', { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('update', () => {
    it('should update an existing freelance', () => {
      const updatedFreelance = { ...mockFreelance, firstName: 'Johnny' };

      service.update(mockFreelance.id!, updatedFreelance).subscribe(freelance => {
        expect(freelance).toEqual(updatedFreelance);
        expect(freelance.firstName).toBe('Johnny');
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${mockFreelance.id}`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(updatedFreelance);
      req.flush(updatedFreelance);
    });
  });

  describe('delete', () => {
    it('should delete a freelance', () => {
      const freelanceId = 1;

      service.delete(freelanceId).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${freelanceId}`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('checkEmailExists', () => {
    it('should check if email exists', () => {
      const email = 'john.doe@email.com';
      const exists = true;

      service.checkEmailExists(email).subscribe(result => {
        expect(result).toBe(exists);
      });

      const req = httpMock.expectOne((request) => {
        return request.url === `${environment.apiUrl}/freelances/exists` &&
               request.params.get('email') === email;
      });
      expect(req.request.method).toBe('GET');
      req.flush(exists);
    });

    it('should return false for non-existent email', () => {
      const email = 'nonexistent@email.com';
      const exists = false;

      service.checkEmailExists(email).subscribe(result => {
        expect(result).toBe(exists);
      });

      const req = httpMock.expectOne((request) => {
        return request.url === `${environment.apiUrl}/freelances/exists` &&
               request.params.get('email') === email;
      });
      req.flush(exists);
    });
  });

  describe('updatePassword', () => {
    it('should update freelance password', () => {
      const freelanceId = 1;
      const newPassword = 'newSecurePassword123';

      service.updatePassword(freelanceId, newPassword).subscribe(response => {
        expect(response).toBeNull();
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${freelanceId}/password`);
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toBe(newPassword);
      req.flush(null);
    });

    it('should handle error when updating password', () => {
      const freelanceId = 999;
      const newPassword = 'newPassword';

      service.updatePassword(freelanceId, newPassword).subscribe({
        next: () => fail('Expected error'),
        error: (error) => {
          expect(error.status).toBe(404);
        }
      });

      const req = httpMock.expectOne(`${environment.apiUrl}/freelances/${freelanceId}/password`);
      req.flush('Not Found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('status validation', () => {
    it('should handle all valid status values', () => {
      const statuses: Array<'AVAILABLE' | 'EMPLOYED' | 'UNAVAILABLE'> = ['AVAILABLE', 'EMPLOYED', 'UNAVAILABLE'];
      
      statuses.forEach(status => {
        const freelanceWithStatus = { ...mockFreelance, status };
        
        service.create(freelanceWithStatus).subscribe(freelance => {
          expect(freelance.status).toBe(status);
        });

        const req = httpMock.expectOne(`${environment.apiUrl}/freelances`);
        req.flush({ ...mockFreelance, status });
      });
    });
  });
});
