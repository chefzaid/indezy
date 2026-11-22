import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { OfferPublisherBlock } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferBlockSuggestionsComponent } from './offer-block-suggestions.component';

describe('OfferBlockSuggestionsComponent', () => {
  afterEach(() => localStorage.removeItem('indezy.offers.declinedBlockSuggestions'));

  function create(declined: string[] = []) {
    localStorage.setItem('indezy.offers.declinedBlockSuggestions', JSON.stringify(declined));
    const offers = jasmine.createSpyObj('OfferService', ['blockSuggestions', 'blockPublisher']);
    offers.blockSuggestions.and.returnValue(of([{ name: 'Grosse ESN', hidden: 7 }, { name: 'Conseil Y', hidden: 6 }, { name: 'Refusée', hidden: 5 }]));
    const notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [OfferBlockSuggestionsComponent, TranslateModule.forRoot()],
      providers: [{ provide: OfferService, useValue: offers }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(OfferBlockSuggestionsComponent);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, offers, notifications };
  }

  it('offers to hide a publisher kept hidden, blocking it or leaving it for good on this device', () => {
    const { fixture, component, offers, notifications } = create(['Refusée']);
    expect(component.suggestions.map(suggestion => suggestion.name)).toEqual(['Grosse ESN', 'Conseil Y']);
    const blocked = jasmine.createSpy('blocked');
    component.blocked.subscribe(blocked);
    offers.blockPublisher.and.returnValue(of({ id: 1, name: 'Grosse ESN' } as OfferPublisherBlock));
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-block-suggestion="Grosse ESN"] [data-block-suggestion-accept]')!.click();
    expect(offers.blockPublisher).toHaveBeenCalledWith('Grosse ESN');
    expect(blocked).toHaveBeenCalledWith('Grosse ESN');
    expect(notifications.success).toHaveBeenCalledWith('offers.blockSuggestion.done', 4000, { name: 'Grosse ESN' });

    component.decline(component.suggestions[0]);
    expect(component.suggestions).toEqual([]);
    expect(JSON.parse(localStorage.getItem('indezy.offers.declinedBlockSuggestions')!)).toEqual(['Refusée', 'Conseil Y']);

    offers.blockPublisher.and.returnValue(throwError(() => new Error('down')));
    component.block({ name: 'X', hidden: 5 });
    expect(notifications.error).toHaveBeenCalledWith('offers.blockSuggestion.error');
  });

  it('shows nothing when the suggestions cannot be read', () => {
    localStorage.setItem('indezy.offers.declinedBlockSuggestions', 'not json');
    const offers = jasmine.createSpyObj('OfferService', ['blockSuggestions']);
    offers.blockSuggestions.and.returnValue(throwError(() => new Error('down')));
    TestBed.configureTestingModule({ imports: [OfferBlockSuggestionsComponent, TranslateModule.forRoot()],
      providers: [{ provide: OfferService, useValue: offers }] });
    const fixture = TestBed.createComponent(OfferBlockSuggestionsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.suggestions).toEqual([]);
  });
});
