import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { convertToParamMap } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferQuery, SavedOfferSearch } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferAlertsService } from '../../../services/offer/offer-alerts.service';
import { defaultOfferQuery, offerQueryFromParams } from '../../../services/offer/offer-query';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { OfferSavedSearchesComponent } from './offer-saved-searches.component';

describe('OfferSavedSearchesComponent', () => {
  let offers: jasmine.SpyObj<OfferService>;
  let alerts: jasmine.SpyObj<OfferAlertsService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let confirm: jasmine.SpyObj<ConfirmDialogService>;
  let dialogResult: string | undefined;

  function search(id: number, name: string, newOffers: number): SavedOfferSearch {
    return { id, name, filters: { q: name.toLowerCase(), sort: 'RATE', view: 'FEED', page: 0, rateMin: null }, lastOpenedAt: '2026-10-18T08:00:00Z', newOffers };
  }

  function create(query: OfferQuery = { ...defaultOfferQuery(), q: 'kafka' },
                  searches: SavedOfferSearch[] = [search(1, 'Java', 3), search(2, 'Python', 0)]): ComponentFixture<OfferSavedSearchesComponent> {
    offers = jasmine.createSpyObj('OfferService', ['savedSearches', 'saveSearch', 'openSearch', 'deleteSearch', 'searchSuggestions',
      'dismissSuggestion']);
    offers.searchSuggestions.and.returnValue(of({ suggestions: [], missing: [] }));
    alerts = jasmine.createSpyObj('OfferAlertsService', ['refresh']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    confirm = jasmine.createSpyObj('ConfirmDialogService', ['confirm']);
    offers.savedSearches.and.returnValue(of(searches));
    offers.openSearch.and.returnValue(of(search(1, 'Java', 0)));
    offers.deleteSearch.and.returnValue(of(undefined));
    TestBed.configureTestingModule({
      imports: [OfferSavedSearchesComponent, TranslateModule.forRoot()],
      providers: [
        { provide: OfferService, useValue: offers },
        { provide: OfferAlertsService, useValue: alerts },
        { provide: NotificationService, useValue: notifications },
        { provide: ConfirmDialogService, useValue: confirm },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(dialogResult) }) } }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferSavedSearchesComponent);
    fixture.componentRef.setInput('query', query);
    fixture.detectChanges();
    return fixture;
  }

  function buttons(fixture: ComponentFixture<OfferSavedSearchesComponent>, selector: string): HTMLButtonElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll(selector));
  }

  function names(fixture: ComponentFixture<OfferSavedSearchesComponent>): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll('.search-name') as NodeListOf<HTMLElement>).map(name => name.textContent!.trim());
  }

  it('lists the saved searches with what is new for each', () => {
    const fixture = create();

    expect(names(fixture)).toEqual(['Java', 'Python']);
    expect(fixture.nativeElement.querySelector('[data-new-count]').textContent.trim()).toBe('3 nouvelles');
    expect(buttons(fixture, '.delete-search')[0].getAttribute('aria-label')).toBe('Supprimer la recherche « Java »');
  });

  it('opens a saved search on the feed and counts its new offers afresh', () => {
    const fixture = create();
    const opened: OfferQuery[] = [];
    fixture.componentInstance.searchOpened.subscribe(query => opened.push(query));

    buttons(fixture, '.open-search')[0].click();
    fixture.detectChanges();

    expect(offers.openSearch).toHaveBeenCalledWith(1);
    expect(alerts.refresh).toHaveBeenCalled();
    expect(opened).toEqual([offerQueryFromParams(convertToParamMap({ q: 'java', sort: 'RATE' }))]);
    expect(fixture.nativeElement.querySelector('[data-new-count]')).toBeNull();
  });

  it('saves the current filters under the name chosen, or says why it cannot', () => {
    const fixture = create();
    dialogResult = 'Kafka';
    offers.saveSearch.and.returnValue(of(search(3, 'Kafka', 0)));

    buttons(fixture, '.save-search')[0].click();
    fixture.detectChanges();

    expect(offers.saveSearch).toHaveBeenCalledWith('Kafka', fixture.componentInstance.query);
    expect(names(fixture)).toEqual(['Java', 'Kafka', 'Python']);
    expect(notifications.success).toHaveBeenCalledWith('offers.savedSearches.saved', 4000, { name: 'Kafka' });

    offers.saveSearch.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    fixture.componentInstance.save();
    expect(notifications.error).toHaveBeenCalledWith('offers.savedSearches.errors.conflict');
    offers.saveSearch.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    fixture.componentInstance.save();
    expect(notifications.error).toHaveBeenCalledWith('offers.savedSearches.errors.save');
  });

  it('saves nothing when the dialog is cancelled or no filter is set', () => {
    const fixture = create({ ...defaultOfferQuery() });
    dialogResult = undefined;

    expect(buttons(fixture, '.save-search')[0].disabled).toBeTrue();
    fixture.componentInstance.save();
    expect(offers.saveSearch).not.toHaveBeenCalled();
  });

  it('deletes a saved search once confirmed', () => {
    const fixture = create();
    confirm.confirm.and.returnValues(of(false), of(true));

    buttons(fixture, '.delete-search')[1].click();
    expect(offers.deleteSearch).not.toHaveBeenCalled();
    buttons(fixture, '.delete-search')[1].click();
    fixture.detectChanges();

    expect(offers.deleteSearch).toHaveBeenCalledWith(2);
    expect(buttons(fixture, '.open-search').length).toBe(1);
    expect(alerts.refresh).toHaveBeenCalled();
    offers.deleteSearch.and.returnValue(throwError(() => new Error('offline')));
    confirm.confirm.and.returnValue(of(true));
    buttons(fixture, '.delete-search')[0].click();
    expect(notifications.error).toHaveBeenCalledWith('offers.savedSearches.errors.delete');
  });

  it('shows no list when nothing is saved yet', () => {
    const fixture = create(undefined, []);

    expect(fixture.nativeElement.querySelector('.search-list')).toBeNull();
  });

  describe('suggested searches', () => {
    afterEach(() => {
      localStorage.removeItem('indezy-offer-suggestions-closed');
      history.replaceState(null, '');
    });

    function suggestions(fixture: ComponentFixture<OfferSavedSearchesComponent>): HTMLElement | null {
      return fixture.nativeElement.querySelector('[data-search-suggestions]');
    }

    it('opens them by itself while nothing is saved, until they are closed', () => {
      const fixture = create(undefined, []);
      expect(suggestions(fixture)).not.toBeNull();

      buttons(fixture, '[data-toggle-suggestions]')[0].click();
      fixture.detectChanges();
      expect(suggestions(fixture)).toBeNull();
      expect(localStorage.getItem('indezy-offer-suggestions-closed')).toBe('true');

      TestBed.resetTestingModule();
      const again = create(undefined, []);
      expect(suggestions(again)).toBeNull();
      buttons(again, '[data-toggle-suggestions]')[0].click();
      again.detectChanges();
      expect(suggestions(again)).not.toBeNull();
      expect(buttons(again, '[data-toggle-suggestions]')[0].getAttribute('aria-expanded')).toBe('true');
      expect(localStorage.getItem('indezy-offer-suggestions-closed')).toBeNull();
    });

    it('keeps them closed beside saved searches unless the page was opened to see them', () => {
      expect(suggestions(create())).toBeNull();

      TestBed.resetTestingModule();
      localStorage.setItem('indezy-offer-suggestions-closed', 'true');
      history.replaceState({ openSuggestions: true }, '');
      expect(suggestions(create())).not.toBeNull();
    });

    it('adds a saved suggestion to the saved searches and previews one in the feed', () => {
      const fixture = create(undefined, [search(2, 'Python', 0)]);
      const opened: OfferQuery[] = [];
      fixture.componentInstance.searchOpened.subscribe(query => opened.push(query));

      fixture.componentInstance.addSaved(search(3, 'Kafka', 0));
      fixture.detectChanges();
      buttons(fixture, '[data-toggle-suggestions]')[0].click();
      fixture.detectChanges();
      const panel = fixture.debugElement.query(debug => debug.name === 'app-offer-search-suggestions');
      panel.componentInstance.preview.emit({ ...defaultOfferQuery(), skills: ['Java'] });

      expect(names(fixture)).toEqual(['Kafka', 'Python']);
      expect(opened[0].skills).toEqual(['Java']);
    });
  });
});
