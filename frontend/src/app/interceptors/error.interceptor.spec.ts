import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, provideHttpClient, withInterceptors, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { errorInterceptor } from './error.interceptor';
import { AuthService } from '../services/auth/auth.service';

describe('errorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(() => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting()
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(errorInterceptor).toBeTruthy();
  });

  describe('401 Unauthorized errors', () => {
    it('should call authService.logout() on 401 error', () => {
      const testUrl = '/api/protected';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(401);
          expect(authService.logout).toHaveBeenCalled();
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should navigate to login page on 401 error', () => {
      const testUrl = '/api/secure';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(401);
          expect(router.navigate).toHaveBeenCalledWith(['/login']);
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
    });

    it('should pass through the 401 error after handling', () => {
      const testUrl = '/api/test';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(401);
          expect(error.statusText).toBe('Unauthorized');
          expect(error.error).toBe('Token expired');
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Token expired', { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('403 Forbidden errors', () => {
    it('should navigate to error page on 403 error', () => {
      const testUrl = '/api/admin';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(403);
          expect(router.navigate).toHaveBeenCalledWith(['/404']);
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Access denied', { status: 403, statusText: 'Forbidden' });
    });

    it('should not call authService.logout() on 403 error', () => {
      const testUrl = '/api/forbidden';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: () => {
          expect(authService.logout).not.toHaveBeenCalled();
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
    });
  });

  describe('404 Not Found errors', () => {
    it('should navigate to error page on 404 error', () => {
      const testUrl = '/api/nonexistent';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(404);
          expect(router.navigate).toHaveBeenCalledWith(['/404']);
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Resource not found', { status: 404, statusText: 'Not Found' });
    });
  });

  describe('500 Server errors', () => {
    it('should navigate to error page on 500 error', () => {
      const testUrl = '/api/server-error';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(500);
          expect(router.navigate).toHaveBeenCalledWith(['/error']);
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Internal server error', { status: 500, statusText: 'Internal Server Error' });
    });

    it('should handle 502 Bad Gateway errors', () => {
      const testUrl = '/api/gateway-error';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(502);
          expect(router.navigate).toHaveBeenCalledWith(['/error']);
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Bad Gateway', { status: 502, statusText: 'Bad Gateway' });
    });

    it('should handle 503 Service Unavailable errors', () => {
      const testUrl = '/api/unavailable';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(503);
          expect(router.navigate).toHaveBeenCalledWith(['/error']);
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Service Unavailable', { status: 503, statusText: 'Service Unavailable' });
    });
  });

  describe('other HTTP errors', () => {
    it('should pass through 400 Bad Request without special handling', () => {
      const testUrl = '/api/bad-request';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(400);
          expect(router.navigate).not.toHaveBeenCalled();
          expect(authService.logout).not.toHaveBeenCalled();
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });
    });

    it('should pass through 422 Unprocessable Entity without special handling', () => {
      const testUrl = '/api/validation-error';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(422);
          expect(router.navigate).not.toHaveBeenCalled();
          expect(authService.logout).not.toHaveBeenCalled();
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.flush('Validation Error', { status: 422, statusText: 'Unprocessable Entity' });
    });
  });

  describe('successful requests', () => {
    it('should pass through successful requests unchanged', () => {
      const testUrl = '/api/success';
      const responseData = { message: 'Success' };

      httpClient.get(testUrl).subscribe(response => {
        expect(response).toEqual(responseData);
        expect(router.navigate).not.toHaveBeenCalled();
        expect(authService.logout).not.toHaveBeenCalled();
      });

      const req = httpMock.expectOne(testUrl);
      req.flush(responseData);
    });

    it('should handle 201 Created responses', () => {
      const testUrl = '/api/create';
      const responseData = { id: 1, name: 'Created' };

      httpClient.post(testUrl, {}).subscribe(response => {
        expect(response).toEqual(responseData);
        expect(router.navigate).not.toHaveBeenCalled();
        expect(authService.logout).not.toHaveBeenCalled();
      });

      const req = httpMock.expectOne(testUrl);
      req.flush(responseData, { status: 201, statusText: 'Created' });
    });
  });

  describe('multiple errors', () => {
    it('should handle multiple 401 errors correctly', () => {
      const urls = ['/api/test1', '/api/test2'];

      urls.forEach(url => {
        httpClient.get(url).subscribe({
          next: () => fail('Expected error'),
          error: (error: HttpErrorResponse) => {
            expect(error.status).toBe(401);
          }
        });
      });

      urls.forEach(url => {
        const req = httpMock.expectOne(url);
        req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });
      });

      expect(authService.logout).toHaveBeenCalledTimes(2);
      expect(router.navigate).toHaveBeenCalledTimes(2);
    });
  });

  describe('error handling edge cases', () => {
    it('should handle network errors', () => {
      const testUrl = '/api/network-error';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(0);
          expect(error.error instanceof ProgressEvent).toBeTruthy();
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.error(new ProgressEvent('error'));
    });

    it('should handle timeout errors', () => {
      const testUrl = '/api/timeout';

      httpClient.get(testUrl).subscribe({
        next: () => fail('Expected error'),
        error: (error: HttpErrorResponse) => {
          expect(error.status).toBe(0);
        }
      });

      const req = httpMock.expectOne(testUrl);
      req.error(new ProgressEvent('timeout'));
    });
  });
});
