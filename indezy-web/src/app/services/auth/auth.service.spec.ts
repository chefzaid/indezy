import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { LoginRequest, LoginResponse, RegisterRequest } from '../../models';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jasmine.SpyObj<Router>;
  let localStorageSpy: jasmine.Spy;

  const mockLoginRequest: LoginRequest = {
    email: 'test@example.com',
    password: 'password123'
  };

  const mockLoginResponse: LoginResponse = {
    token: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwidXNlcklkIjoxLCJpYXQiOjE2MzQ1Njc4OTAsImV4cCI6MTYzNDY1NDI5MH0.test-signature',
    user: {
      id: 1,
      email: 'test@example.com',
      firstName: 'John',
      lastName: 'Doe'
    }
  };

  const mockRegisterRequest: RegisterRequest = {
    firstName: 'John',
    lastName: 'Doe',
    email: 'test@example.com',
    password: 'password123'
  };

  beforeEach(() => {
    const routerSpyObj = jasmine.createSpyObj('Router', ['navigate']);
    
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: routerSpyObj }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    // Mock localStorage
    localStorageSpy = spyOn(localStorage, 'getItem').and.returnValue(null);
    spyOn(localStorage, 'setItem');
    spyOn(localStorage, 'removeItem');
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('login', () => {
    it('should login successfully with valid credentials', () => {
      service.login(mockLoginRequest).subscribe({
        next: (response) => {
          expect(response.token).toBe(mockLoginResponse.token);
          expect(response.user).toEqual(mockLoginResponse.user);
          expect(localStorage.setItem).toHaveBeenCalledWith('indezy_token', response.token);
          expect(localStorage.setItem).toHaveBeenCalledWith('indezy_user', JSON.stringify(response.user));
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(mockLoginRequest);
      req.flush(mockLoginResponse);
    });

    it('should handle login error with invalid credentials', () => {
      const invalidCredentials = { email: '', password: '' };

      service.login(invalidCredentials).subscribe({
        error: (error) => {
          expect(error.status).toBe(401);
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
      expect(req.request.method).toBe('POST');
      req.flush({}, { status: 401, statusText: 'Unauthorized' });
    });

    it('should update currentUser$ observable on successful login', () => {
      service.login(mockLoginRequest).subscribe();

      const req = httpMock.expectOne('http://localhost:8080/api/auth/login');
      req.flush(mockLoginResponse);

      service.currentUser$.subscribe(user => {
        if (user) {
          expect(user).toEqual(mockLoginResponse.user);
        }
      });
    });
  });

  describe('register', () => {
    it('should register successfully with valid data', () => {
      service.register(mockRegisterRequest).subscribe({
        next: (response) => {
          expect(response.user.email).toBe(mockRegisterRequest.email);
          expect(response.user.firstName).toBe(mockRegisterRequest.firstName);
          expect(response.user.lastName).toBe(mockRegisterRequest.lastName);
          expect(localStorage.setItem).toHaveBeenCalledWith('indezy_token', jasmine.any(String));
          expect(localStorage.setItem).toHaveBeenCalledWith('indezy_user', jasmine.any(String));
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/auth/register');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(mockRegisterRequest);
      req.flush(mockLoginResponse);
    });

    it('should handle registration error with invalid data', () => {
      const invalidData = { firstName: '', lastName: '', email: '', password: '' };

      service.register(invalidData).subscribe({
        error: (error) => {
          expect(error.status).toBe(400);
        }
      });

      const req = httpMock.expectOne('http://localhost:8080/api/auth/register');
      expect(req.request.method).toBe('POST');
      req.flush({}, { status: 400, statusText: 'Bad Request' });
    });
  });

  describe('logout', () => {
    it('should clear localStorage and navigate to login', () => {
      service.logout();

      expect(localStorage.removeItem).toHaveBeenCalledWith('indezy_token');
      expect(localStorage.removeItem).toHaveBeenCalledWith('indezy_user');
      expect(routerSpy.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('should update currentUser$ to null on logout', () => {
      // Set a user first
      service['currentUserSubject'].next({
        id: 1,
        email: 'test@example.com',
        firstName: 'John',
        lastName: 'Doe'
      });

      service.logout();

      service.currentUser$.subscribe(user => {
        expect(user).toBeNull();
      });
    });
  });

  describe('getToken', () => {
    it('should return token from localStorage', () => {
      localStorageSpy.and.returnValue('test-token');
      
      const token = service.getToken();
      
      expect(token).toBe('test-token');
      expect(localStorage.getItem).toHaveBeenCalledWith('indezy_token');
    });

    it('should return null when no token exists', () => {
      localStorageSpy.and.returnValue(null);
      
      const token = service.getToken();
      
      expect(token).toBeNull();
    });
  });

  describe('getUser', () => {
    it('should return user from localStorage', () => {
      const mockUser = {
        id: 1,
        email: 'test@example.com',
        firstName: 'Test',
        lastName: 'User'
      };
      localStorageSpy.and.returnValue(JSON.stringify(mockUser));
      
      const user = service.getUser();
      
      expect(user).toEqual(mockUser);
    });

    it('should return null when no user exists', () => {
      localStorageSpy.and.returnValue(null);
      
      const user = service.getUser();
      
      expect(user).toBeNull();
    });
  });

  describe('isAuthenticated', () => {
    it('should return false when no token exists', () => {
      spyOn(service, 'getToken').and.returnValue(null);
      
      const isAuth = service.isAuthenticated();
      
      expect(isAuth).toBeFalse();
    });

    it('should return true for valid JWT token', () => {
      const futureTime = Math.floor(Date.now() / 1000) + 3600; // 1 hour from now
      const validPayload = { exp: futureTime };
      const validToken = 'header.' + btoa(JSON.stringify(validPayload)) + '.signature';

      spyOn(service, 'getToken').and.returnValue(validToken);

      const isAuth = service.isAuthenticated();

      expect(isAuth).toBeTrue();
    });

    it('should return false for expired JWT token', () => {
      const pastTime = Math.floor(Date.now() / 1000) - 3600; // 1 hour ago
      const expiredPayload = { exp: pastTime };
      const expiredToken = 'header.' + btoa(JSON.stringify(expiredPayload)) + '.signature';

      spyOn(service, 'getToken').and.returnValue(expiredToken);

      const isAuth = service.isAuthenticated();

      expect(isAuth).toBeFalse();
    });

    it('should return false for invalid JWT token format', () => {
      spyOn(service, 'getToken').and.returnValue('invalid-token-format');

      const isAuth = service.isAuthenticated();

      expect(isAuth).toBeFalse();
    });

    it('should return false for expired JWT token', () => {
      const pastTime = Math.floor(Date.now() / 1000) - 3600; // 1 hour ago
      const expiredPayload = { exp: pastTime };
      const expiredToken = 'header.' + btoa(JSON.stringify(expiredPayload)) + '.signature';
      
      spyOn(service, 'getToken').and.returnValue(expiredToken);
      
      const isAuth = service.isAuthenticated();
      
      expect(isAuth).toBeFalse();
    });

    it('should return false for malformed JWT token', () => {
      spyOn(service, 'getToken').and.returnValue('invalid-token');
      
      const isAuth = service.isAuthenticated();
      
      expect(isAuth).toBeFalse();
    });
  });
});
