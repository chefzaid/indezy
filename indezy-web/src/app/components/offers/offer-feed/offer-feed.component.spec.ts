import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { Offer, OfferApplicationRequest, OfferPage } from '../../../models/offer.models';
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
      'savedSearches', 'alerts', 'market', 'blockPublisher', 'apply', 'withdrawApplication', 'searchSuggestions', 'marketTrends']);
    service.marketTrends.and.returnValue(of({ skills: [] }));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    service.search.and.returnValue(of(result));
    service.searchSuggestions.and.returnValue(of({ suggestions: [], missing: [] }));
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

    expect(service.search).toHaveBeenCalledWith(jasmine.objectContaining({ q: 'java', view: 'SAVED', page: 0 }), undefined);
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

  it('reuses the total of the filters shown for another page or sort, not for other filters', () => {
    const component = create({ q: 'java' }, page([offer(1), offer(2)], { totalElements: 41, totalPages: 3 })).componentInstance;
    component.drop(offer => offer.id === 1);

    params.next(convertToParamMap({ q: 'java', page: '1', sort: 'RATE' }));
    expect(service.search.calls.mostRecent().args[1]).toBe(40);

    params.next(convertToParamMap({ q: 'kotlin', page: '1' }));
    expect(service.search.calls.mostRecent().args[1]).toBeUndefined();
  });

  it('counts again after a failed search', () => {
    create({ q: 'java' });
    service.search.and.returnValue(throwError(() => new Error('down')));
    params.next(convertToParamMap({ q: 'java', page: '1' }));

    params.next(convertToParamMap({ q: 'java', page: '2' }));

    expect(service.search.calls.mostRecent().args[1]).toBeUndefined();
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

    component.actions.toggleSaved(component.page!.content[0]);
    expect(component.page!.content[0].saved).toBeTrue();
    expect(service.updateState).toHaveBeenCalledWith(1, { saved: true });

    service.updateState.and.returnValue(throwError(() => new Error('down')));
    component.actions.toggleSaved(component.page!.content[1]);
    expect(component.page!.content[1].saved).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.state');
  });

  it('takes an offer out of the view it leaves', () => {
    const feed = create();
    feed.componentInstance.actions.toggleHidden(feed.componentInstance.page!.content[0]);
    expect(feed.componentInstance.page!.content.map(o => o.id)).toEqual([2]);
    expect(feed.componentInstance.page!.totalElements).toBe(1);

    TestBed.resetTestingModule();
    const saved = create({ view: 'SAVED' }, page([offer(1, { saved: true })]));
    saved.componentInstance.actions.toggleSaved(saved.componentInstance.page!.content[0]);
    expect(saved.componentInstance.page!.content).toEqual([]);

    TestBed.resetTestingModule();
    const hidden = create({ view: 'HIDDEN' }, page([offer(1, { hidden: true })]));
    hidden.componentInstance.actions.toggleHidden(hidden.componentInstance.page!.content[0]);
    expect(hidden.componentInstance.page!.content).toEqual([]);
  });

  it('marks an opened offer as seen once', () => {
    const component = create().componentInstance;

    component.actions.markSeen(component.page!.content[0]);
    component.actions.markSeen(component.page!.content[0]);

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

    component.actions.importOffer(component.page!.content[0]);

    expect(component.page!.content[0].importedProjectId).toBe(40);
    expect(notifications.success).toHaveBeenCalledWith('offers.notifications.imported', 4000, { role: 'Mission 1' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.pipeline-link')).not.toBeNull();
  });

  it('explains an offer already in the pipeline and other import failures', () => {
    const component = create().componentInstance;
    service.importOffer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    service.get.and.returnValue(of(offer(1, { importedProjectId: 41 })));

    component.actions.importOffer(component.page!.content[0]);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.alreadyImported');
    expect(component.page!.content[0].importedProjectId).toBe(41);

    service.importOffer.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.actions.importOffer(component.page!.content[1]);
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

  it('hides every offer of a publisher the user blocks, whatever the spelling', () => {
    const fixture = create({}, page([offer(1, { company: 'Exemple Conseil' }), offer(2, { company: 'EXEMPLE-conseil' }),
      offer(3, { company: 'Acme' })], { totalElements: 30 }));
    const component = fixture.componentInstance;
    service.blockPublisher.and.returnValue(of({ id: 8, name: 'Exemple Conseil' }));

    component.actions.blockPublisher(component.page!.content[0]);

    expect(service.blockPublisher).toHaveBeenCalledWith('Exemple Conseil');
    expect(component.page!.content.map(item => item.id)).toEqual([3]);
    expect(component.page!.totalElements).toBe(28);
    expect(notifications.success).toHaveBeenCalledWith('offers.notifications.publisherBlocked', 6000, { publisher: 'Exemple Conseil' });
    service.blockPublisher.and.returnValue(throwError(() => new Error('down')));
    component.actions.blockPublisher(component.page!.content[0]);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.block');
    component.actions.blockPublisher(offer(4));
    expect(service.blockPublisher).toHaveBeenCalledTimes(2);
  });

  it('keeps a note on an offer at once and puts the old one back when the server refuses', () => {
    const fixture = create({}, page([offer(1, { note: 'Ancienne' }), offer(2)]));
    const component = fixture.componentInstance;
    service.updateState.and.returnValue(of({ offerId: 2, saved: false, hidden: false, seen: false, note: 'Rappeler' }));

    component.actions.saveNote(component.page!.content[1], ' Rappeler ');
    expect(service.updateState).toHaveBeenCalledWith(2, { note: ' Rappeler ' });
    expect(component.page!.content[1].note).toBe('Rappeler');

    service.updateState.and.returnValue(of({ offerId: 1, saved: false, hidden: false, seen: false }));
    component.actions.saveNote(component.page!.content[0], '');
    expect(component.page!.content[0].note).toBeUndefined();

    service.updateState.and.returnValue(throwError(() => new Error('down')));
    component.actions.saveNote(component.page!.content[1], 'Autre');
    expect(component.page!.content[1].note).toBe('Rappeler');
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.note');
  });

  it('records an application from the dialog in the interface language and shows it on the card', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const request: OfferApplicationRequest = { appliedOn: '2026-10-20', via: 'EMAIL', followUpInDays: 7 };
    const open = spyOn(TestBed.inject(MatDialog), 'open').and.returnValues(
      { afterClosed: () => of(request) } as MatDialogRef<unknown>, { afterClosed: () => of(undefined) } as MatDialogRef<unknown>);
    service.apply.and.returnValue(of({ offerId: 1, saved: false, hidden: false, seen: true, importedProjectId: 40,
      application: { appliedOn: '2026-10-20', via: 'EMAIL' } }));

    component.actions.applyToOffer(component.page!.content[0]);
    component.actions.applyToOffer(component.page!.content[1]);

    expect(open.calls.first().args[1]?.data).toEqual({ offer: jasmine.objectContaining({ id: 1 }) });
    expect(service.apply).toHaveBeenCalledOnceWith(1, { ...request, language: 'FR' });
    expect(component.page!.content[0]).toEqual(jasmine.objectContaining({ importedProjectId: 40, seen: true,
      application: { appliedOn: '2026-10-20', via: 'EMAIL' } }));
    expect(notifications.success).toHaveBeenCalledWith('offers.notifications.appliedWithFollowUp', 6000, { role: 'Mission 1' });
    expect(text(fixture)).toContain("Candidature");
  });

  it('explains a second application or a failure', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    spyOn(TestBed.inject(MatDialog), 'open').and.returnValue(
      { afterClosed: () => of({ appliedOn: '2026-10-20', via: 'PHONE' }) } as MatDialogRef<unknown>);
    service.apply.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    service.get.and.returnValue(of(offer(1, { importedProjectId: 40, application: { appliedOn: '2026-10-18', via: 'EMAIL' } })));

    component.actions.applyToOffer(component.page!.content[0]);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.alreadyApplied');
    expect(component.page!.content[0].application).toEqual({ appliedOn: '2026-10-18', via: 'EMAIL' });

    service.apply.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.actions.applyToOffer(component.page!.content[1]);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.apply');
    expect(component.page!.content[1].application).toBeUndefined();
  });

  it('takes an application back at once, out of the applied view, and puts it back on failure', () => {
    const application = { appliedOn: '2026-10-20', via: 'EMAIL' as const };
    const fixture = create({ view: 'APPLIED' }, page([offer(1, { application }), offer(2, { application })]));
    const component = fixture.componentInstance;
    service.withdrawApplication.and.returnValue(of({ offerId: 1, saved: false, hidden: false, seen: true }));

    component.actions.withdrawApplication(component.page!.content[0]);
    expect(component.page!.content.map(item => item.id)).toEqual([2]);
    expect(component.page!.totalElements).toBe(1);
    expect(notifications.success).toHaveBeenCalledWith('offers.notifications.withdrawn', 4000);

    service.withdrawApplication.and.returnValue(throwError(() => new Error('down')));
    component.actions.withdrawApplication(component.page!.content[0]);
    expect(component.page!.content[0].application).toEqual(application);
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.state');
    expect(fixture.nativeElement.querySelector('[data-view="APPLIED"]')).not.toBeNull();
  });

  it('keeps the saved offers of a blocked publisher in the saved view', () => {
    const fixture = create({ view: 'SAVED' }, page([offer(1, { company: 'Acme', saved: true })]));
    service.blockPublisher.and.returnValue(of({ id: 8, name: 'Acme' }));

    fixture.componentInstance.actions.blockPublisher(fixture.componentInstance.page!.content[0]);

    expect(fixture.componentInstance.page!.content.length).toBe(1);
  });

  it('acts on the offer a key is pressed on', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.get.and.returnValue(of(offer(2, { description: 'Contexte' })));
    service.importOffer.and.returnValue(of({ id: 40 } as ProjectDto));

    component.onOfferKey({ action: 'save', index: 0 });
    component.onOfferKey({ action: 'details', index: 1 });
    component.onOfferKey({ action: 'add', index: 0 });
    component.onOfferKey({ action: 'add', index: 0 });
    component.onOfferKey({ action: 'hide', index: 1 });
    component.onOfferKey({ action: 'save', index: 9 });
    component.onOfferKey({ action: 'note', index: 0 });
    component.onOfferKey({ action: 'select', index: 0 });
    fixture.detectChanges();

    expect(service.updateState).toHaveBeenCalledWith(1, { saved: true });
    expect(service.get).toHaveBeenCalledWith(2);
    expect(service.importOffer).toHaveBeenCalledTimes(1);
    expect(service.updateState).toHaveBeenCalledWith(2, { hidden: true });
    expect(fixture.nativeElement.querySelector('[data-offer-item] [data-note-editor]')).not.toBeNull();
    expect(component.bulk.selected.has(1)).toBeTrue();
    expect(fixture.nativeElement.querySelector('[data-selection-bar]')).not.toBeNull();
    params.next(convertToParamMap({ page: '1' }));
    expect(component.bulk.selected.size).toBe(0);
    expect(fixture.nativeElement.querySelector('ol[aria-describedby="offer-keys-hint"]')).not.toBeNull();
    expect(text(fixture)).toContain('Utilisez J et K');
  });

  it('links to the job boards and warns when one fails', () => {
    const fixture = create();
    const link = fixture.nativeElement.querySelector('[data-sites-link]') as HTMLAnchorElement;

    expect(link.getAttribute('href')).toBe('/offers/sites');
    expect(fixture.nativeElement.querySelector('[data-sites-to-visit-link]').getAttribute('href')).toBe('/offers/sites#sites-to-visit');
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
