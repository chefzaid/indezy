import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors, HttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authService: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['getToken']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(authInterceptor).toBeTruthy();
  });

  describe('when token exists', () => {
    beforeEach(() => {
      authService.getToken.and.returnValue('mock-jwt-token-123');
    });

    it('should add Authorization header to API requests', () => {
      const testUrl = '/api/test';
      const testData = { message: 'test' };

      httpClient.get(testUrl).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeTruthy();
      expect(req.request.headers.get('Authorization')).toBe('Bearer mock-jwt-token-123');
      
      req.flush(testData);
    });

    it('should add Authorization header to POST requests', () => {
      const testUrl = '/api/users';
      const postData = { name: 'John Doe' };
      const responseData = { id: 1, name: 'John Doe' };

      httpClient.post(testUrl, postData).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeTruthy();
      expect(req.request.headers.get('Authorization')).toBe('Bearer mock-jwt-token-123');
      expect(req.request.body).toEqual(postData);
      
      req.flush(responseData);
    });

    it('should add Authorization header to PUT requests', () => {
      const testUrl = '/api/users/1';
      const putData = { id: 1, name: 'Jane Doe' };

      httpClient.put(testUrl, putData).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeTruthy();
      expect(req.request.headers.get('Authorization')).toBe('Bearer mock-jwt-token-123');
      
      req.flush(putData);
    });

    it('should add Authorization header to DELETE requests', () => {
      const testUrl = '/api/users/1';

      httpClient.delete(testUrl).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeTruthy();
      expect(req.request.headers.get('Authorization')).toBe('Bearer mock-jwt-token-123');
      
      req.flush({});
    });

    it('should preserve existing headers', () => {
      const testUrl = '/api/test';
      const customHeaders = { 'Custom-Header': 'custom-value' };

      httpClient.get(testUrl, { headers: customHeaders }).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeTruthy();
      expect(req.request.headers.has('Custom-Header')).toBeTruthy();
      expect(req.request.headers.get('Authorization')).toBe('Bearer mock-jwt-token-123');
      expect(req.request.headers.get('Custom-Header')).toBe('custom-value');
      
      req.flush({});
    });

    it('should not override existing Authorization header', () => {
      const testUrl = '/api/test';
      const existingAuth = 'Bearer existing-token';

      httpClient.get(testUrl, { 
        headers: { 'Authorization': existingAuth } 
      }).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.get('Authorization')).toBe(existingAuth);
      
      req.flush({});
    });
  });

  describe('when token does not exist', () => {
    beforeEach(() => {
      authService.getToken.and.returnValue(null);
    });

    it('should not add Authorization header', () => {
      const testUrl = '/api/test';

      httpClient.get(testUrl).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeFalsy();
      
      req.flush({});
    });

    it('should pass through request unchanged', () => {
      const testUrl = '/api/public';
      const customHeaders = { 'Content-Type': 'application/json' };

      httpClient.get(testUrl, { headers: customHeaders }).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeFalsy();
      expect(req.request.headers.has('Content-Type')).toBeTruthy();
      expect(req.request.headers.get('Content-Type')).toBe('application/json');
      
      req.flush({});
    });
  });

  describe('when token is empty string', () => {
    beforeEach(() => {
      authService.getToken.and.returnValue('');
    });

    it('should not add Authorization header for empty token', () => {
      const testUrl = '/api/test';

      httpClient.get(testUrl).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.has('Authorization')).toBeFalsy();
      
      req.flush({});
    });
  });

  describe('multiple requests', () => {
    beforeEach(() => {
      authService.getToken.and.returnValue('mock-token-456');
    });

    it('should add Authorization header to multiple concurrent requests', () => {
      const urls = ['/api/users', '/api/projects', '/api/clients'];

      urls.forEach(url => {
        httpClient.get(url).subscribe();
      });

      urls.forEach(url => {
        const req = httpMock.expectOne(url);
        expect(req.request.headers.has('Authorization')).toBeTruthy();
        expect(req.request.headers.get('Authorization')).toBe('Bearer mock-token-456');
        req.flush({});
      });
    });

    it('should handle token changes between requests', () => {
      // First request with initial token
      authService.getToken.and.returnValue('token-1');
      httpClient.get('/api/test1').subscribe();

      let req = httpMock.expectOne('/api/test1');
      expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
      req.flush({});

      // Second request with updated token
      authService.getToken.and.returnValue('token-2');
      httpClient.get('/api/test2').subscribe();

      req = httpMock.expectOne('/api/test2');
      expect(req.request.headers.get('Authorization')).toBe('Bearer token-2');
      req.flush({});
    });
  });

  describe('error scenarios', () => {
    it('should handle AuthService.getToken throwing error', () => {
      authService.getToken.and.throwError('Token service error');
      spyOn(console, 'warn');

      httpClient.get('/api/test').subscribe();

      const req = httpMock.expectOne('/api/test');
      expect(req.request.headers.has('Authorization')).toBeFalsy();
      expect(console.warn).toHaveBeenCalledWith('Failed to get auth token:', jasmine.any(Error));

      req.flush({});
    });

    it('should handle undefined token', () => {
      authService.getToken.and.returnValue(undefined as any);

      httpClient.get('/api/test').subscribe();

      const req = httpMock.expectOne('/api/test');
      expect(req.request.headers.has('Authorization')).toBeFalsy();
      
      req.flush({});
    });
  });

  describe('request types', () => {
    beforeEach(() => {
      authService.getToken.and.returnValue('test-token');
    });

    it('should handle PATCH requests', () => {
      const testUrl = '/api/users/1';
      const patchData = { name: 'Updated Name' };

      httpClient.patch(testUrl, patchData).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
      expect(req.request.method).toBe('PATCH');
      
      req.flush({});
    });

    it('should handle OPTIONS requests', () => {
      const testUrl = '/api/test';

      httpClient.request('OPTIONS', testUrl).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
      expect(req.request.method).toBe('OPTIONS');
      
      req.flush({});
    });

    it('should handle HEAD requests', () => {
      const testUrl = '/api/test';

      httpClient.head(testUrl).subscribe();

      const req = httpMock.expectOne(testUrl);
      expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
      expect(req.request.method).toBe('HEAD');
      
      req.flush({});
    });
  });
});
