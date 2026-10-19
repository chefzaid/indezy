import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { OfferService } from './offer.service';
import { defaultOfferQuery } from './offer-query';

describe('OfferService', () => {
  const api = `${environment.apiUrl}/offers`;
  let service: OfferService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(OfferService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('searches with the query as parameters', () => {
    service.search({ ...defaultOfferQuery(), q: 'java', sites: ['FREE_WORK', 'YEETS'], page: 2 }).subscribe();

    const request = http.expectOne(req => req.url === api);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('q')).toBe('java');
    expect(request.request.params.getAll('sites')).toEqual(['FREE_WORK', 'YEETS']);
    expect(request.request.params.get('page')).toBe('2');
    request.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0, distanceAvailable: false });
  });

  it('reads one offer, changes its state and imports it', () => {
    service.get(7).subscribe();
    http.expectOne(`${api}/7`).flush({});

    service.updateState(7, { saved: true }).subscribe();
    const state = http.expectOne(`${api}/7/state`);
    expect(state.request.method).toBe('PUT');
    expect(state.request.body).toEqual({ saved: true });
    state.flush({});

    service.importOffer(7).subscribe();
    const imported = http.expectOne(`${api}/7/import`);
    expect(imported.request.method).toBe('POST');
    imported.flush({ id: 40 });
  });

  it('lists sites, skills and regions', () => {
    service.sites().subscribe();
    http.expectOne(`${api}/sites`).flush([]);
    service.skills('ja').subscribe();
    const skills = http.expectOne(req => req.url === `${api}/skills`);
    expect(skills.request.params.get('q')).toBe('ja');
    skills.flush([]);
    service.regions().subscribe();
    http.expectOne(`${api}/regions`).flush([]);
  });
});
