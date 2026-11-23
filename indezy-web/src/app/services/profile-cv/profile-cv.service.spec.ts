import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { ProfileCvService } from './profile-cv.service';

describe('ProfileCvService', () => {
  let service: ProfileCvService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/users/cv`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ProfileCvService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the CV kept, none when the server answers without content', () => {
    const answers: unknown[] = [];
    service.info().subscribe(cv => answers.push(cv));
    http.expectOne(`${url}/info`).flush(null, { status: 204, statusText: 'No Content' });
    service.info().subscribe(cv => answers.push(cv));
    http.expectOne(`${url}/info`).flush({ fileName: 'cv.pdf' });
    expect(answers).toEqual([null, { fileName: 'cv.pdf' }]);
  });

  it('keeps, downloads and removes the CV', () => {
    service.keep(new File(['%PDF'], 'cv.pdf')).subscribe();
    expect(http.expectOne(url).request.body instanceof FormData).toBeTrue();
    service.download().subscribe();
    expect(http.expectOne(url).request.responseType).toBe('blob');
    service.remove().subscribe();
    expect(http.expectOne(url).request.method).toBe('DELETE');
  });
});
