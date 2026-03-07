import { TestBed } from '@angular/core/testing';
import { Router, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth/auth.service';

describe('authGuard', () => {
  let authService: jasmine.SpyObj<AuthService>;
  let router: jasmine.SpyObj<Router>;
  let mockRoute: ActivatedRouteSnapshot;
  let mockState: RouterStateSnapshot;

  beforeEach(() => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['isAuthenticated']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy }
      ]
    });

    authService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;

    // Create mock route and state
    mockRoute = {} as ActivatedRouteSnapshot;
    mockState = {
      url: '/dashboard'
    } as RouterStateSnapshot;
  });

  it('should be created', () => {
    expect(authGuard).toBeTruthy();
  });

  describe('when user is authenticated', () => {
    beforeEach(() => {
      authService.isAuthenticated.and.returnValue(true);
    });

    it('should allow access', () => {
      const result = TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, mockState)
      );

      expect(result).toBe(true);
      expect(authService.isAuthenticated).toHaveBeenCalled();
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should not redirect to login', () => {
      TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, mockState)
      );

      expect(router.navigate).not.toHaveBeenCalled();
    });
  });

  describe('when user is not authenticated', () => {
    beforeEach(() => {
      authService.isAuthenticated.and.returnValue(false);
    });

    it('should deny access', () => {
      const result = TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, mockState)
      );

      expect(result).toBe(false);
      expect(authService.isAuthenticated).toHaveBeenCalled();
    });

    it('should redirect to login page', () => {
      TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, mockState)
      );

      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: mockState.url }
      });
    });

    it('should include return URL in query params', () => {
      const customState = { url: '/projects/123' } as RouterStateSnapshot;

      TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, customState)
      );

      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/projects/123' }
      });
    });

    it('should handle empty URL', () => {
      const emptyState = { url: '' } as RouterStateSnapshot;

      TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, emptyState)
      );

      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '' }
      });
    });

    it('should handle root URL', () => {
      const rootState = { url: '/' } as RouterStateSnapshot;

      TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, rootState)
      );

      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/' }
      });
    });
  });

  describe('edge cases', () => {
    it('should handle AuthService throwing error', () => {
      authService.isAuthenticated.and.throwError('Service error');

      expect(() => {
        TestBed.runInInjectionContext(() => 
          authGuard(mockRoute, mockState)
        );
      }).toThrowError('Service error');
    });

    it('should work with different route configurations', () => {
      const routeWithParams = {
        params: { id: '123' },
        queryParams: { tab: 'details' },
        url: [],
        fragment: null,
        data: {},
        outlet: 'primary',
        component: null,
        routeConfig: null,
        root: {} as ActivatedRouteSnapshot,
        parent: null,
        firstChild: null,
        children: [],
        pathFromRoot: [],
        paramMap: jasmine.createSpyObj('ParamMap', ['get']),
        queryParamMap: jasmine.createSpyObj('ParamMap', ['get']),
        title: undefined
      } as ActivatedRouteSnapshot;

      authService.isAuthenticated.and.returnValue(true);

      const result = TestBed.runInInjectionContext(() => 
        authGuard(routeWithParams, mockState)
      );

      expect(result).toBe(true);
      expect(authService.isAuthenticated).toHaveBeenCalled();
    });

    it('should work with complex URLs', () => {
      const complexState = { 
        url: '/clients/123/contacts/456?tab=edit&mode=full' 
      } as RouterStateSnapshot;

      authService.isAuthenticated.and.returnValue(false);

      TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, complexState)
      );

      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/clients/123/contacts/456?tab=edit&mode=full' }
      });
    });
  });

  describe('integration scenarios', () => {
    it('should work in a typical protected route scenario', () => {
      // Simulate accessing a protected dashboard route
      const dashboardState = { url: '/dashboard' } as RouterStateSnapshot;
      authService.isAuthenticated.and.returnValue(true);

      const result = TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, dashboardState)
      );

      expect(result).toBe(true);
      expect(router.navigate).not.toHaveBeenCalled();
    });

    it('should work in a typical unauthorized access scenario', () => {
      // Simulate unauthorized access to projects
      const projectsState = { url: '/projects' } as RouterStateSnapshot;
      authService.isAuthenticated.and.returnValue(false);

      const result = TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, projectsState)
      );

      expect(result).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: '/projects' }
      });
    });

    it('should handle session expiry scenario', () => {
      // First call returns true (user was authenticated)
      // Second call returns false (session expired)
      authService.isAuthenticated.and.returnValues(true, false);

      // First access - should succeed
      let result = TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, mockState)
      );
      expect(result).toBe(true);

      // Second access - should fail and redirect
      result = TestBed.runInInjectionContext(() => 
        authGuard(mockRoute, mockState)
      );
      expect(result).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/login'], {
        queryParams: { returnUrl: mockState.url }
      });
    });
  });
});
