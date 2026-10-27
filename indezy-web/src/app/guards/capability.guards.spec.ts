import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of } from 'rxjs';

import { adminGuard, featureGuard } from './capability.guards';
import { Capabilities } from '../models';
import { CapabilitiesService, DEFAULT_CAPABILITIES } from '../services/admin/capabilities.service';

describe('capability guards', () => {
  let capabilities: Capabilities;

  beforeEach(() => {
    capabilities = DEFAULT_CAPABILITIES;
    const service = {
      get: () => of(capabilities),
      isEnabled: (flag: keyof Capabilities['features']) => of(capabilities.features[flag])
    };
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: CapabilitiesService, useValue: service }] });
  });

  async function resolve(guard: typeof adminGuard): Promise<string | boolean> {
    const result = TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
    const value = await firstValueFrom(result as Observable<boolean | UrlTree>);
    return typeof value === 'boolean' ? value : TestBed.inject(Router).serializeUrl(value);
  }

  it('opens a feature that is on, and sends back to the dashboard one turned off', async () => {
    expect(await resolve(featureGuard('OFFER_FEED'))).toBeTrue();
    capabilities = { ...DEFAULT_CAPABILITIES, features: { ...DEFAULT_CAPABILITIES.features, OFFER_FEED: false } };
    expect(await resolve(featureGuard('OFFER_FEED'))).toBe('/dashboard');
    expect(await resolve(featureGuard('YEARLY_REVIEW'))).toBeTrue();
  });

  it('opens the admin console to administrators only', async () => {
    expect(await resolve(adminGuard)).toBe('/dashboard');
    capabilities = { ...DEFAULT_CAPABILITIES, admin: true };
    expect(await resolve(adminGuard)).toBeTrue();
  });
});
