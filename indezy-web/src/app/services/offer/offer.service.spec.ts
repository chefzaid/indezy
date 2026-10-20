import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environment';
import { OfferService } from './offer.service';
import { defaultOfferQuery } from './offer-query';
import { SILENT_HTTP_ERRORS } from '../../interceptors/error.interceptor';

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

  it('reads the health of the job boards, excludes one and reads one on request without the generic error message', () => {
    service.siteHealth().subscribe();
    http.expectOne(`${api}/sites/health`).flush({ sites: [], leftOut: [], refreshEnabled: true });

    service.setSiteExcluded('YEETS', true).subscribe();
    const exclusion = http.expectOne(`${api}/sites/YEETS/exclusion`);
    expect(exclusion.request.method).toBe('PUT');
    expect(exclusion.request.body).toEqual({ excluded: true });
    expect(exclusion.request.context.get(SILENT_HTTP_ERRORS)).toBeTrue();
    exclusion.flush({ site: 'YEETS', excluded: true });

    service.refreshSite('YEETS').subscribe();
    const refresh = http.expectOne(`${api}/sites/YEETS/refresh`);
    expect(refresh.request.method).toBe('POST');
    expect(refresh.request.context.get(SILENT_HTTP_ERRORS)).toBeTrue();
    refresh.flush({ site: 'YEETS', refreshAvailableAt: '2026-10-19T10:15:00Z' });
  });

  it('records and forgets a visit of a site Indezy does not read', () => {
    service.visitSite('MALT').subscribe();
    const visit = http.expectOne(`${api}/sites/left-out/MALT/visit`);
    expect(visit.request.method).toBe('PUT');
    expect(visit.request.context.get(SILENT_HTTP_ERRORS)).toBeTrue();
    visit.flush({ site: 'MALT', visitedAt: '2026-10-20T10:00:00Z' });

    service.clearSiteVisit('MALT').subscribe();
    const clear = http.expectOne(`${api}/sites/left-out/MALT/visit`);
    expect(clear.request.method).toBe('DELETE');
    clear.flush({ site: 'MALT', visitedAt: null });
  });

  it('lists, blocks and unblocks the publishers the workspace hides', () => {
    service.blockedPublishers().subscribe();
    http.expectOne(`${api}/blocked-publishers`).flush([]);

    service.blockPublisher('Acme').subscribe();
    const block = http.expectOne(`${api}/blocked-publishers`);
    expect(block.request.method).toBe('POST');
    expect(block.request.body).toEqual({ name: 'Acme' });
    block.flush({ id: 9, name: 'Acme' });

    service.unblockPublisher(9).subscribe();
    const unblock = http.expectOne(`${api}/blocked-publishers/9`);
    expect(unblock.request.method).toBe('DELETE');
    unblock.flush(null);
  });

  it('describes the market of the offers a query finds', () => {
    service.market({ ...defaultOfferQuery(), q: 'java', workModes: ['REMOTE'] }).subscribe();

    const request = http.expectOne(req => req.url === `${api}/market`);
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('q')).toBe('java');
    expect(request.request.params.getAll('workModes')).toEqual(['REMOTE']);
    request.flush({ offers: 0, rates: { stated: 0 }, rateBins: [], remote: { remote: 0, hybrid: 0, onsite: 0, unstated: 0 },
      skills: [], intermediaries: [], perDay: [] });
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

  it('records an application and forgets it', () => {
    service.apply(7, { appliedOn: '2026-10-20', via: 'EMAIL', followUpInDays: 7, language: 'FR' }).subscribe();
    const applied = http.expectOne(`${api}/7/application`);
    expect(applied.request.method).toBe('POST');
    expect(applied.request.body).toEqual({ appliedOn: '2026-10-20', via: 'EMAIL', followUpInDays: 7, language: 'FR' });
    applied.flush({ offerId: 7, saved: false, hidden: false, seen: true, importedProjectId: 40 });

    service.withdrawApplication(7).subscribe();
    const withdrawn = http.expectOne(`${api}/7/application`);
    expect(withdrawn.request.method).toBe('DELETE');
    withdrawn.flush({ offerId: 7, saved: false, hidden: false, seen: true });
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

  it('saves, lists, opens and deletes searches and counts what is new for them', () => {
    service.saveSearch('Java', { ...defaultOfferQuery(), q: 'java' }).subscribe();
    const saved = http.expectOne(`${api}/searches`);
    expect(saved.request.method).toBe('POST');
    expect(saved.request.body).toEqual(jasmine.objectContaining({ name: 'Java', filters: jasmine.objectContaining({ q: 'java' }) }));
    saved.flush({ id: 3 });

    service.savedSearches().subscribe();
    http.expectOne(req => req.method === 'GET' && req.url === `${api}/searches`).flush([]);
    service.openSearch(3).subscribe();
    expect(http.expectOne(`${api}/searches/3/open`).request.method).toBe('POST');
    service.deleteSearch(3).subscribe();
    expect(http.expectOne(`${api}/searches/3`).request.method).toBe('DELETE');
    service.alerts().subscribe(alerts => expect(alerts.newOffers).toBe(4));
    const alerts = http.expectOne(`${api}/searches/alerts`);
    expect(alerts.request.context.get(SILENT_HTTP_ERRORS)).toBeTrue();
    alerts.flush({ newOffers: 4 });
  });
});
