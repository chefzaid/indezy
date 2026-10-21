import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferTrend } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferMarketTrendsComponent } from './offer-market-trends.component';

describe('OfferMarketTrendsComponent', () => {
  const java: OfferTrend = {
    skills: ['Java', 'Kafka'], skill: 'Java', regionName: 'Île-de-France',
    latest: { day: '2026-10-21', median: 630, offers: 1200 },
    changes: [{ days: 30, medianPercent: 5, offersPercent: -25 }, { days: 90, medianPercent: 0, offersPercent: 12 }, { days: 365 }]
  };
  let offers: jasmine.SpyObj<OfferService>;

  function create(answer: Observable<OfferTrend>, region?: string): ComponentFixture<OfferMarketTrendsComponent> {
    offers = jasmine.createSpyObj('OfferService', ['marketTrends']);
    offers.marketTrends.and.returnValue(answer);
    TestBed.configureTestingModule({
      imports: [OfferMarketTrendsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: LOCALE_ID, useValue: 'fr-FR' }, { provide: OfferService, useValue: offers }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferMarketTrendsComponent);
    fixture.componentRef.setInput('region', region);
    fixture.detectChanges();
    return fixture;
  }

  function cells(fixture: ComponentFixture<OfferMarketTrendsComponent>, selector: string): string[] {
    return Array.from(fixture.nativeElement.querySelectorAll(selector) as NodeListOf<HTMLElement>)
      .map(cell => cell.textContent!.replace(/\s+/g, ' ').trim());
  }

  it('compares the latest night with 30, 90 and 365 days before, in the feed\'s region', () => {
    const fixture = create(of(java), '11');

    expect(offers.marketTrends).toHaveBeenCalledOnceWith(undefined, '11');
    expect(cells(fixture, 'thead th')).toEqual(['Chiffre', 'Dernière nuit', '30 jours', '90 jours', '1 an']);
    expect(cells(fixture, 'tbody tr:first-child td')).toEqual(['630 €', '+5 %', '0 %', '—']);
    expect(cells(fixture, 'tbody tr:last-child td')).toEqual(['1 200', '−25 %', '+12 %', '—']);
    expect(fixture.nativeElement.querySelector('caption').textContent).toContain('Java en Île-de-France');
    const toned = Array.from(fixture.nativeElement.querySelectorAll('tbody tr:first-child td') as NodeListOf<HTMLElement>).slice(1)
      .map(cell => cell.className);
    expect(toned).toEqual(['up', 'flat', 'none']);
  });

  it('follows another skill of the profile and another region', () => {
    const fixture = create(of(java));
    fixture.componentInstance.choose('Kafka');
    expect(offers.marketTrends).toHaveBeenCalledWith('Kafka', undefined);

    fixture.componentRef.setInput('region', '84');
    fixture.detectChanges();
    expect(offers.marketTrends).toHaveBeenCalledWith('Kafka', '84');
  });

  it('says when the profile names no skill or the skill has no history yet', () => {
    const none = create(of({ skills: [] }));
    expect(none.nativeElement.querySelector('[data-trends-no-skill]')).not.toBeNull();
    expect(none.nativeElement.querySelector('[data-trend-skill]')).toBeNull();

    TestBed.resetTestingModule();
    const fresh = create(of({ skills: ['Java'], skill: 'Java' }));
    expect(fresh.nativeElement.querySelector('[data-trends-no-history]').textContent).toContain('Java en France');
  });

  it('offers to try again after a failure', () => {
    const fixture = create(throwError(() => new Error('down')));
    expect(fixture.nativeElement.textContent).toContain('Les tendances n\'ont pas pu être chargées.');

    offers.marketTrends.and.returnValue(of(java));
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(cells(fixture, 'tbody tr:first-child td')[0]).toBe('630 €');
  });
});
