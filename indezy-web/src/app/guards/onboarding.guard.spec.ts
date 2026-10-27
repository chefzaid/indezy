import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { onboardingGuard } from './onboarding.guard';
import { AuthService } from '../services/auth/auth.service';
import { User } from '../models/auth.models';

describe('onboardingGuard', () => {
  let auth: jasmine.SpyObj<AuthService>;

  beforeEach(() => {
    auth = jasmine.createSpyObj<AuthService>('AuthService', ['getUser']);
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
  });

  function resolve(): string | boolean {
    const result = TestBed.runInInjectionContext(() => onboardingGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    return typeof result === 'boolean' ? result : TestBed.inject(Router).serializeUrl(result as UrlTree);
  }

  it('sends a new account to its welcome steps', () => {
    auth.getUser.and.returnValue({ id: 1, onboardingPending: true } as User);
    expect(resolve()).toBe('/welcome');
  });

  it('lets a welcomed account, or one from before the welcome steps, through', () => {
    auth.getUser.and.returnValue({ id: 1, onboardingPending: false } as User);
    expect(resolve()).toBeTrue();
    auth.getUser.and.returnValue({ id: 1 } as User);
    expect(resolve()).toBeTrue();
    auth.getUser.and.returnValue(null);
    expect(resolve()).toBeTrue();
  });
});
