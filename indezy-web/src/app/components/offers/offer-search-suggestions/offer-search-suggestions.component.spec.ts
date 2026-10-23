import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferQuery, OfferSearchSuggestion, OfferSearchSuggestions, SavedOfferSearch } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferSearchSuggestionsComponent } from './offer-search-suggestions.component';

describe('OfferSearchSuggestionsComponent', () => {
  const java: OfferSearchSuggestion = {
    key: 'java', filters: { skills: ['Java'], rateMin: 600, radiusKm: 30, regions: [], view: 'FEED', sort: 'NEWEST', page: 0, unseenOnly: false },
    place: 'Lyon', offers: 12, goodMatches: 3, newThisWeek: 1
  };
  const remote: OfferSearchSuggestion = {
    key: 'remote', filters: { skills: ['Java'], workModes: ['REMOTE'] }, offers: 1, goodMatches: 0, newThisWeek: 0
  };
  let offers: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogResult: string | undefined;
  let dialogName: string | undefined;

  function create(answer: OfferSearchSuggestions = { suggestions: [java, remote], missing: ['POSITION', 'CITY'] }):
    ComponentFixture<OfferSearchSuggestionsComponent> {
    offers = jasmine.createSpyObj('OfferService', ['searchSuggestions', 'dismissSuggestion', 'saveSearch']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    offers.searchSuggestions.and.returnValue(of(answer));
    TestBed.configureTestingModule({
      imports: [OfferSearchSuggestionsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: OfferService, useValue: offers },
        { provide: NotificationService, useValue: notifications },
        { provide: MatDialog, useValue: { open: (_: unknown, config: { data: { name: string } }) => {
          dialogName = config.data.name;
          return { afterClosed: () => of(dialogResult) };
        } } }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferSearchSuggestionsComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferSearchSuggestionsComponent>, selector: string): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll(selector) as NodeListOf<HTMLElement>)
      .map(element => element.textContent!.replace(/\s+/g, ' ').trim());
  }

  function click(fixture: ComponentFixture<OfferSearchSuggestionsComponent>, selector: string, index = 0): void {
    (fixture.nativeElement.querySelectorAll(selector)[index] as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  it('says what each suggestion keeps and finds, and what the profile lacks', () => {
    const fixture = create();

    expect(text(fixture, '.suggestion-name')).toEqual(['Java · 600 €/jour ou plus · Lyon + 30 km', 'Java · Full remote']);
    expect(text(fixture, '.suggestion-counts')).toEqual([
      '12 offres ouvertes · 3 correspondent à votre profil · 1 nouvelle cette semaine', '1 offre ouverte']);
    expect(text(fixture, '[data-missing-profile]')[0]).toContain('renseignez dans votre profil : intitulé de poste, ville.');
  });

  it('previews a suggestion in the feed', () => {
    const fixture = create();
    const previewed: OfferQuery[] = [];
    fixture.componentInstance.preview.subscribe(query => previewed.push(query));

    click(fixture, '[data-preview-suggestion]');

    expect(previewed[0]).toEqual(jasmine.objectContaining({ skills: ['Java'], rateMin: 600, radiusKm: 30, view: 'FEED', page: 0 }));
  });

  it('saves a suggestion under a name it offers', () => {
    const fixture = create();
    const saved: SavedOfferSearch[] = [];
    fixture.componentInstance.saved.subscribe(search => saved.push(search));
    const search: SavedOfferSearch = { id: 9, name: 'Java à Lyon', filters: java.filters, lastOpenedAt: '2026-10-21T08:00:00Z', newOffers: 0,
      emails: { frequency: 'OFF', hour: null, language: null, stoppedAt: null }, emailsAvailable: false };
    offers.saveSearch.and.returnValue(of(search));
    dialogResult = 'Java à Lyon';

    click(fixture, '[data-save-suggestion]');

    expect(dialogName).toBe('Java · 600 €/jour ou plus · Lyon + 30 km');
    expect(offers.saveSearch).toHaveBeenCalledWith('Java à Lyon', jasmine.objectContaining({ skills: ['Java'], radiusKm: 30 }));
    expect(saved).toEqual([search]);
    expect(text(fixture, '.suggestion-name')).toEqual(['Java · Full remote']);
    expect(notifications.success).toHaveBeenCalledWith('offers.savedSearches.saved', 4000, { name: 'Java à Lyon' });
  });

  it('tells a name already taken and saves nothing when the dialog is closed', () => {
    const fixture = create();
    offers.saveSearch.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    dialogResult = 'Java';
    click(fixture, '[data-save-suggestion]');
    expect(notifications.error).toHaveBeenCalledWith('offers.savedSearches.errors.conflict');
    offers.saveSearch.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    click(fixture, '[data-save-suggestion]');
    expect(notifications.error).toHaveBeenCalledWith('offers.savedSearches.errors.save');

    dialogResult = undefined;
    offers.saveSearch.calls.reset();
    click(fixture, '[data-save-suggestion]');
    expect(offers.saveSearch).not.toHaveBeenCalled();
  });

  it('dismisses a suggestion at once and puts it back if the server refuses', () => {
    const fixture = create();
    offers.dismissSuggestion.and.returnValue(of(undefined));
    click(fixture, '[data-dismiss-suggestion]', 1);
    expect(offers.dismissSuggestion).toHaveBeenCalledWith(remote.filters);
    expect(text(fixture, '.suggestion-name')).toEqual(['Java · 600 €/jour ou plus · Lyon + 30 km']);

    offers.dismissSuggestion.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    click(fixture, '[data-dismiss-suggestion]');
    expect(text(fixture, '.suggestion-name')).toEqual(['Java · 600 €/jour ou plus · Lyon + 30 km']);
    expect(notifications.error).toHaveBeenCalledWith('offers.suggestions.errors.dismiss');
  });

  it('says when there is nothing to suggest, and offers to try again after a failure', () => {
    const empty = create({ suggestions: [], missing: [] });
    expect(empty.nativeElement.querySelector('[data-no-suggestion]')).not.toBeNull();
    expect(empty.nativeElement.querySelector('[data-missing-profile]')).toBeNull();

    TestBed.resetTestingModule();
    const failing = create();
    offers.searchSuggestions.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    failing.componentInstance.load();
    failing.detectChanges();
    expect(failing.nativeElement.textContent).toContain('Les suggestions n\'ont pas pu être chargées.');
    offers.searchSuggestions.and.returnValue(of({ suggestions: [remote], missing: [] }));
    click(failing, '.hint button');
    expect(text(failing, '.suggestion-name')).toEqual(['Java · Full remote']);
  });
});
