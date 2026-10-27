import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Capabilities } from '../../models';
import { CapabilitiesService, DEFAULT_CAPABILITIES } from './capabilities.service';

describe('CapabilitiesService', () => {
  const url = `${environment.apiUrl}/users/capabilities`;
  const adminWithoutOffers: Capabilities = {
    admin: true,
    features: { OFFER_FEED: false, PUBLIC_PROFILES: true, DATA_IMPORT: true, YEARLY_REVIEW: true }
  };
  let http: HttpTestingController;
  let service: CapabilitiesService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(CapabilitiesService);
  });

  afterEach(() => http.verify());

  it('turns every feature on and the admin console off before the first read', () => {
    expect(service.snapshot()).toEqual(DEFAULT_CAPABILITIES);
  });

  it('reads them once, then answers from what it read', async () => {
    const first = firstValueFrom(service.isEnabled('OFFER_FEED'));
    http.expectOne(url).flush(adminWithoutOffers);
    expect(await first).toBeFalse();
    expect(await firstValueFrom(service.isEnabled('YEARLY_REVIEW'))).toBeTrue();
    expect(service.snapshot()).toEqual(adminWithoutOffers);
    expect(await firstValueFrom(service.capabilities$)).toEqual(adminWithoutOffers);
  });

  it('falls back to every feature on when they cannot be read', async () => {
    const read = firstValueFrom(service.get());
    http.expectOne(url).flush(null, { status: 500, statusText: 'Server Error' });
    expect(await read).toEqual(DEFAULT_CAPABILITIES);
  });

  it('keeps a feature it is not told about on', async () => {
    const read = firstValueFrom(service.refresh());
    http.expectOne(url).flush({ admin: true, features: { DATA_IMPORT: false } });
    expect(await read).toEqual({ admin: true, features: { ...DEFAULT_CAPABILITIES.features, DATA_IMPORT: false } });
  });

  it('forgets them at sign-out and reads them again at the next sign-in', async () => {
    const read = firstValueFrom(service.refresh());
    http.expectOne(url).flush(adminWithoutOffers);
    await read;
    service.clear();
    expect(service.snapshot()).toEqual(DEFAULT_CAPABILITIES);
    const again = firstValueFrom(service.get());
    http.expectOne(url).flush(DEFAULT_CAPABILITIES);
    expect(await again).toEqual(DEFAULT_CAPABILITIES);
  });
});
