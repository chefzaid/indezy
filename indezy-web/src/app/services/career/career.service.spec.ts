import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { CareerService } from './career.service';

describe('CareerService', () => {
  let service: CareerService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/users`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CareerService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('keeps the past positions', () => {
    const details = { employer: 'Banque', title: 'Dev', shownPublicly: false };
    service.experiences().subscribe();
    http.expectOne(`${url}/experiences`).flush([]);
    service.createExperience(details).subscribe();
    expect(http.expectOne(`${url}/experiences`).request.body).toEqual(details);
    service.updateExperience(3, details).subscribe();
    expect(http.expectOne(`${url}/experiences/3`).request.method).toBe('PUT');
    service.deleteExperience(3).subscribe();
    expect(http.expectOne(`${url}/experiences/3`).request.method).toBe('DELETE');
  });

  it('keeps the schools', () => {
    const details = { school: 'INSA', shownPublicly: true };
    service.educations().subscribe();
    http.expectOne(`${url}/educations`).flush([]);
    service.createEducation(details).subscribe();
    expect(http.expectOne(`${url}/educations`).request.method).toBe('POST');
    service.updateEducation(4, details).subscribe();
    expect(http.expectOne(`${url}/educations/4`).request.method).toBe('PUT');
    service.deleteEducation(4).subscribe();
    expect(http.expectOne(`${url}/educations/4`).request.method).toBe('DELETE');
  });
});
