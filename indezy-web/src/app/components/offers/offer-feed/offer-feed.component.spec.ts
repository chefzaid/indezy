import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideNativeDateAdapter } from '@angular/material/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { Offer, OfferPage } from '../../../models/offer.models';
import { ProjectDto } from '../../../models/project.models';
import { OfferService } from '../../../services/offer/offer.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferFeedComponent } from './offer-feed.component';

describe('OfferFeedComponent', () => {
  let service: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let navigate: jasmine.Spy;

  function offer(id: number, overrides: Partial<Offer> = {}): Offer {
    return {
      id, site: 'FREE_WORK', siteName: 'Free-Work', url: `https://www.free-work.com/${id}`, title: `Mission ${id}`, excerpt: 'Résumé',
      skills: [], publishedAt: '2026-10-19T08:00:00Z', datePrecision: 'EXACT', expired: false, saved: false, hidden: false,
      seen: false, ...overrides
    };
  }

  function page(content: Offer[], overrides: Partial<OfferPage> = {}): OfferPage {
    return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, distanceAvailable: false, matchAvailable: false,
      ...overrides };
  }

  function create(query: Record<string, string> = {}, result: OfferPage = page([offer(1), offer(2)]),
                  before: () => void = () => undefined): ComponentFixture<OfferFeedComponent> {
    registerLocaleData(localeFr);
    service = jasmine.createSpyObj('OfferService', ['search', 'get', 'updateState', 'importOffer', 'sites', 'regions', 'skills',
      'savedSearches', 'alerts', 'market']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    service.search.and.returnValue(of(result));
    service.sites.and.returnValue(of([
      { site: 'FREE_WORK', name: 'Free-Work', homeUrl: 'https://www.free-work.com', openOffers: 120,
        lastSuccessAt: new Date(Date.now() - 2 * 3_600_000).toISOString(), failing: false },
      { site: 'YEETS', name: 'Yeets', homeUrl: 'https://www.yeets.fr', openOffers: 30, failing: true }
    ]));
    service.regions.and.returnValue(of([]));
    service.savedSearches.and.returnValue(of([]));
    service.alerts.and.returnValue(of({ newOffers: 0 }));
    service.market.and.returnValue(of({ offers: 2, rates: { stated: 0 }, rateBins: [], remote: { remote: 0, hybrid: 0, onsite: 0, unstated: 2 },
      skills: [{ value: 'Kafka', offers: 1 }], intermediaries: [], perDay: [] }));
    service.updateState.and.returnValue(of({ offerId: 1, saved: true, hidden: false, seen: true }));
    params = new BehaviorSubject(convertToParamMap(query));
    TestBed.configureTestingModule({
      imports: [OfferFeedComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNativeDateAdapter(),
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: ActivatedRoute, useValue: { queryParamMap: params.asObservable() } },
        { provide: OfferService, useValue: service },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    before();
    const fixture = TestBed.createComponent(OfferFeedComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferFeedComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ');
  }

  function lastQueryParams(): Record<string, unknown> {
    return navigate.calls.mostRecent().args[1].queryParams;
  }

  it('searches with the filters of the address and lists the offers', () => {
    const fixture = create({ q: 'java', view: 'SAVED' });

    expect(service.search).toHaveBeenCalledWith(jasmine.objectContaining({ q: 'java', view: 'SAVED', page: 0 }));
    expect(text(fixture)).toContain('Missions freelance');
    expect(text(fixture)).toContain('150 offres ouvertes sur 2 sites');
    expect(text(fixture)).toContain('2 offres');
    expect(fixture.nativeElement.querySelectorAll('app-offer-card').length).toBe(2);
    expect(fixture.componentInstance.lastUpdate).toContain('il y a');
  });

  it('keeps every change of filters, view, sort and page in the address', () => {
    const component = create({ q: 'java', page: '3' }).componentInstance;

    component.onFiltersChange({ ...component.filters, q: 'kotlin', sites: ['YEETS'] });
    expect(lastQueryParams()).toEqual({ q: 'kotlin', sites: ['YEETS'] });
    component.setView('HIDDEN');
    expect(lastQueryParams()).toEqual({ q: 'java', view: 'HIDDEN' });
    component.setSort('RATE');
    expect(lastQueryParams()).toEqual({ q: 'java', sort: 'RATE' });
    component.onPage({ pageIndex: 2, pageSize: 50, length: 300 });
    expect(lastQueryParams()).toEqual({ q: 'java', page: '2', size: '50' });
    component.clearFilters();
    expect(lastQueryParams()).toEqual({});
  });

  it('sorts by match and stops asking for skills once the profile names some', () => {
    const fixture = create({}, page([offer(1)]));
    const component = fixture.componentInstance;

    expect(component.sortUnavailable('MATCH')).toBeTrue();
    expect(component.sortUnavailable('DISTANCE')).toBeTrue();
    expect(component.sortUnavailable('RATE')).toBeFalse();
    expect(text(fixture)).toContain('Ajoutez vos compétences à votre profil');
    expect(fixture.nativeElement.querySelector('[data-match-hint] a').getAttribute('href')).toBe('/profile');

    service.search.and.returnValue(of(page([offer(1, { match: { score: 80, skills: ['Java'] } })], { matchAvailable: true })));
    params.next(convertToParamMap({ sort: 'MATCH' }));

    expect(service.search.calls.mostRecent().args[0].sort).toBe('MATCH');
    expect(component.sortUnavailable('MATCH')).toBeFalse();
    expect(text(fixture)).not.toContain('Ajoutez vos compétences à votre profil');
  });

  it('searches again when the address changes', () => {
    create();

    params.next(convertToParamMap({ sort: 'RATE' }));

    expect(service.search).toHaveBeenCalledTimes(2);
    expect(service.search.calls.mostRecent().args[0].sort).toBe('RATE');
  });

  it('explains a failed search and tries again', () => {
    const fixture = create();
    service.search.and.returnValue(throwError(() => new Error('down')));
    params.next(convertToParamMap({ q: 'x' }));

    expect(text(fixture)).toContain('Les offres n\'ont pas pu être chargées.');
    service.search.and.returnValue(of(page([offer(3)])));
    fixture.componentInstance.retry();

    expect(fixture.componentInstance.failed).toBeFalse();
    expect(text(fixture)).toContain('1 offre');
  });

  it('says why a view is empty and offers to clear the filters', () => {
    const fixture = create({ view: 'SAVED', rateMin: '500' }, page([]));

    expect(text(fixture)).toContain('Vous n\'avez sauvegardé aucune offre.');
    expect(text(fixture)).toContain('Effacer filtres');
  });

  it('saves an offer at once and undoes it when the server refuses', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    component.toggleSaved(component.page!.content[0]);
    expect(component.page!.content[0].saved).toBeTrue();
    expect(service.updateState).toHaveBeenCalledWith(1, { saved: true });

    service.updateState.and.returnValue(throwError(() => new Error('down')));
    component.toggleSaved(component.page!.content[1]);
    expect(component.page!.content[1].saved).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.state');
  });

  it('takes an offer out of the view it leaves', () => {
    const feed = create();
    feed.componentInstance.toggleHidden(feed.componentInstance.page!.content[0]);
    expect(feed.componentInstance.page!.content.map(o => o.id)).toEqual([2]);
    expect(feed.componentInstance.page!.totalElements).toBe(1);

    TestBed.resetTestingModule();
    const saved = create({ view: 'SAVED' }, page([offer(1, { saved: true })]));
    saved.componentInstance.toggleSaved(saved.componentInstance.page!.content[0]);
    expect(saved.componentInstance.page!.content).toEqual([]);

    TestBed.resetTestingModule();
    const hidden = create({ view: 'HIDDEN' }, page([offer(1, { hidden: true })]));
    hidden.componentInstance.toggleHidden(hidden.componentInstance.page!.content[0]);
    expect(hidden.componentInstance.page!.content).toEqual([]);
  });

  it('marks an opened offer as seen once', () => {
    const component = create().componentInstance;

    component.opened(component.page!.content[0]);
    component.opened(component.page!.content[0]);

    expect(service.updateState).toHaveBeenCalledOnceWith(1, { seen: true });
    expect(component.page!.content[0].seen).toBeTrue();
  });

  it('loads the full description once and folds it back', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.get.and.returnValue(of(offer(1, { description: 'Description complète' })));

    component.toggleDetails(component.page!.content[0]);
    expect(component.expanded.has(1)).toBeTrue();
    expect(text(fixture)).toContain('Description complète');
    component.toggleDetails(component.page!.content[0]);
    expect(component.expanded.has(1)).toBeFalse();
    component.toggleDetails(component.page!.content[0]);
    expect(service.get).toHaveBeenCalledTimes(1);

    params.next(convertToParamMap({ sort: 'RATE' }));
    expect(component.expanded.size).toBe(0);

    service.get.and.returnValue(throwError(() => new Error('down')));
    component.toggleDetails(component.page!.content[1]);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.details');
    expect(component.loadingDetails.has(2)).toBeFalse();
  });

  it('adds an offer to the pipeline and links it to its project', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.importOffer.and.returnValue(of({ id: 40 } as ProjectDto));

    component.importOffer(component.page!.content[0]);

    expect(component.page!.content[0].importedProjectId).toBe(40);
    expect(notifications.success).toHaveBeenCalledWith('offers.notifications.imported', 4000, { role: 'Mission 1' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pipeline-link')).not.toBeNull();
  });

  it('explains an offer already in the pipeline and other import failures', () => {
    const component = create().componentInstance;
    service.importOffer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    service.get.and.returnValue(of(offer(1, { importedProjectId: 41 })));

    component.importOffer(component.page!.content[0]);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.alreadyImported');
    expect(component.page!.content[0].importedProjectId).toBe(41);

    service.importOffer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.importOffer(component.page!.content[1]);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.import');
    expect(component.importing.size).toBe(0);
  });

  it('shows the feed even when the sites and regions cannot be listed', () => {
    const fixture = create({}, page([offer(1)]), () => {
      service.sites.and.returnValue(throwError(() => new Error('down')));
      service.regions.and.returnValue(throwError(() => new Error('down')));
    });

    expect(fixture.componentInstance.openOffers).toBe(0);
    expect(fixture.componentInstance.lastUpdate).toBeNull();
    expect(text(fixture)).toContain('1 offre');
  });

  it('shows the query of a saved search when one is opened', () => {
    const fixture = create();

    fixture.componentInstance.openSavedSearch({ ...fixture.componentInstance.query, q: 'java', rateMin: 600 });

    expect(navigate).toHaveBeenCalledWith([], jasmine.objectContaining({ queryParams: { q: 'java', rateMin: '600' } }));
    expect(fixture.nativeElement.querySelector('app-offer-saved-searches')).not.toBeNull();
  });

  it('links to the job boards and warns when one fails', () => {
    const fixture = create();
    const link = fixture.nativeElement.querySelector('[data-sites-link]') as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/offers/sites');
    expect(text(fixture)).toContain('1 site en échec');
  });

  it('leaves the sites the workspace excludes out of its counts and filters', () => {
    const fixture = create({}, page([offer(1)]), () => service.sites.and.returnValue(of([
      { site: 'FREE_WORK', name: 'Free-Work', homeUrl: 'https://www.free-work.com', openOffers: 120, failing: false },
      { site: 'YEETS', name: 'Yeets', homeUrl: 'https://www.yeets.fr', openOffers: 30, failing: true, excluded: true }
    ])));

    expect(text(fixture)).toContain('120 offres ouvertes sur 1 sites');
    expect(text(fixture)).toContain("Sites d'offres");
    expect(fixture.componentInstance.keptSites.map(site => site.site)).toEqual(['FREE_WORK']);
  });

  it('opens the market snapshot, remembers it and filters on the skills it names', () => {
    localStorage.removeItem('indezy.offers.marketOpen');
    const fixture = create({ skills: 'Java' });
    const toggle = fixture.nativeElement.querySelector('[data-market-toggle]') as HTMLButtonElement;

    expect(fixture.nativeElement.querySelector('app-offer-market')).toBeNull();
    toggle.click();
    fixture.detectChanges();

    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(localStorage.getItem('indezy.offers.marketOpen')).toBe('true');
    expect(service.market).toHaveBeenCalledWith(jasmine.objectContaining({ skills: ['Java'] }));
    (fixture.nativeElement.querySelector('[data-market-skills] button') as HTMLButtonElement).click();
    expect(lastQueryParams()['skills']).toEqual(['Java', 'Kafka']);
    toggle.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-offer-market')).toBeNull();
    expect(localStorage.getItem('indezy.offers.marketOpen')).toBe('false');
    localStorage.removeItem('indezy.offers.marketOpen');
  });

  it('shows the market snapshot at once when it was left open', () => {
    localStorage.setItem('indezy.offers.marketOpen', 'true');
    const fixture = create();
    localStorage.removeItem('indezy.offers.marketOpen');

    expect(fixture.componentInstance.marketOpen).toBeTrue();
    expect(text(fixture)).toContain('Aperçu du marché');
  });
});
