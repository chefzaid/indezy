import { TestBed } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferWordSuggestionsComponent } from './offer-word-suggestions.component';

describe('OfferWordSuggestionsComponent', () => {
  afterEach(() => localStorage.removeItem('indezy.offers.declinedWordSuggestions'));

  function create(declined: unknown = [], exclude?: string) {
    localStorage.setItem('indezy.offers.declinedWordSuggestions', JSON.stringify(declined));
    const offers = jasmine.createSpyObj('OfferService', ['wordSuggestions']);
    offers.wordSuggestions.and.returnValue(of([{ word: 'defense', sector: 'defense', hidden: 7 },
      { word: 'banque', sector: 'banking', hidden: 6 }, { word: 'casino', sector: 'gaming', hidden: 5 }]));
    TestBed.configureTestingModule({
      imports: [OfferWordSuggestionsComponent, TranslateModule.forRoot()],
      providers: [{ provide: OfferService, useValue: offers }]
    });
    const fixture = TestBed.createComponent(OfferWordSuggestionsComponent);
    fixture.componentInstance.exclude = exclude;
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  it('offers to leave out a sector word the hidden offers keep mentioning, or to leave it for good on this device', () => {
    const { fixture, component } = create(['casino']);
    expect(component.shown.map(suggestion => suggestion.word)).toEqual(['defense', 'banque']);
    const leftOut = jasmine.createSpy('leftOut');
    component.leftOut.subscribe(leftOut);
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[data-word-suggestion="defense"] [data-word-suggestion-accept]')!.click();
    expect(leftOut).toHaveBeenCalledWith('defense');
    expect(component.shown.map(suggestion => suggestion.word)).toEqual(['banque']);

    component.decline(component.shown[0]);
    expect(component.shown).toEqual([]);
    expect(JSON.parse(localStorage.getItem('indezy.offers.declinedWordSuggestions')!)).toEqual(['casino', 'banque']);
  });

  it('skips the words the search leaves out already, whatever their accents', () => {
    const { component } = create('not a list', 'Défense  x');
    expect(component.shown.map(suggestion => suggestion.word)).toEqual(['banque', 'casino']);
  });

  it('shows nothing when the suggestions cannot be read', () => {
    localStorage.setItem('indezy.offers.declinedWordSuggestions', 'not json');
    const offers = jasmine.createSpyObj('OfferService', ['wordSuggestions']);
    offers.wordSuggestions.and.returnValue(throwError(() => new Error('down')));
    TestBed.configureTestingModule({ imports: [OfferWordSuggestionsComponent, TranslateModule.forRoot()],
      providers: [{ provide: OfferService, useValue: offers }] });
    const fixture = TestBed.createComponent(OfferWordSuggestionsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.suggestions).toEqual([]);
  });
});
