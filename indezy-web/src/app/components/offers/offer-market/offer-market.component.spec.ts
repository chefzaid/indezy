import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferMarket, OfferQuery } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { defaultOfferQuery } from '../../../services/offer/offer-query';
import { OfferMarketComponent } from './offer-market.component';

describe('OfferMarketComponent', () => {
  let service: jasmine.SpyObj<OfferService>;

  function market(overrides: Partial<OfferMarket> = {}): OfferMarket {
    return {
      offers: 42,
      rates: { stated: 30, firstQuartile: 520, median: 600, thirdQuartile: 680, target: 650, meetTarget: 40 },
      rateBins: [{ to: 500, offers: 2 }, { from: 500, to: 600, offers: 10 }, { from: 600, to: 700, offers: 15 }, { from: 700, offers: 3 }],
      remote: { remote: 10, hybrid: 21, onsite: 7, unstated: 4, hybridRemoteDays: 2.5 },
      skills: [{ value: 'Java', offers: 20 }, { value: 'Kafka', offers: 5 }],
      intermediaries: [{ value: 'Exemple Conseil', offers: 6 }, { value: 'Acme', offers: 3 }],
      perDay: [{ day: '2026-10-18', offers: 3 }, { day: '2026-10-19', offers: 5 }],
      ...overrides
    };
  }

  function create(query: OfferQuery = defaultOfferQuery(), result: Observable<OfferMarket> = of(market())): ComponentFixture<OfferMarketComponent> {
    registerLocaleData(localeFr);
    service = jasmine.createSpyObj('OfferService', ['market']);
    service.market.and.returnValue(result);
    TestBed.configureTestingModule({
      imports: [OfferMarketComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: LOCALE_ID, useValue: 'fr-FR' }, { provide: OfferService, useValue: service }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferMarketComponent);
    fixture.componentRef.setInput('query', query);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferMarketComponent>, selector = '.market'): string {
    fixture.detectChanges();
    return ((fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement).innerText.replace(/\s+/g, ' ').trim();
  }

  it('shows the rates, where the target stands, the remote split and the rankings', () => {
    const fixture = create();
    const component = fixture.componentInstance;

    expect(text(fixture)).toContain('Aperçu du marché');
    expect(text(fixture, '[data-kpi="offers"]')).toBe('Offres 42');
    expect(text(fixture, '[data-kpi="median"]')).toBe('TJM médian 600 €');
    expect(text(fixture, '[data-kpi="quartiles"]')).toBe('Moitié centrale des TJM 520–680 €');
    expect(text(fixture, '[data-kpi="stated"]')).toBe('TJM indiqué 71 %');
    expect(text(fixture, '[data-market-target]')).toContain('40 % des offres indiquant un TJM atteignent ou dépassent votre cible de 650 €.');
    expect(component.view?.bins.labels.map(label => label.replace(/\s/g, ' '))).toEqual(['< 500 €', '500–600 €', '600–700 €', '≥ 700 €']);
    expect(component.view?.bins.target).toBe(2);
    expect(fixture.nativeElement.querySelector('[data-market-rates] canvas').getAttribute('aria-label').replace(/\s/g, ' '))
      .toContain('TJM proposés; < 500 €: 2 offres; 500–600 €: 10 offres');
    expect(text(fixture, '[data-market-rates]')).toContain('Tranche de votre TJM cible');
    expect(text(fixture, '[data-remote="remote"]')).toBe('Full remote 24 % (10)');
    expect(text(fixture, '[data-remote="unstated"]')).toBe('Non indiqué 10 % (4)');
    expect(text(fixture, '[data-market-remote]')).toContain('Offres hybrides : 2,5 jours de télétravail par semaine en moyenne');
    expect(text(fixture, '[data-market-intermediaries]')).toContain('Exemple Conseil 6');
    expect(component.barWidth({ value: 'Acme', offers: 3 }, market().intermediaries)).toBe(50);
    expect(component.view?.days.labels).toEqual(['18 oct.', '19 oct.']);
  });

  it('filters on a skill it names, unless the feed already does', () => {
    const fixture = create({ ...defaultOfferQuery(), skills: ['java'] });
    const chosen: string[] = [];
    fixture.componentInstance.skillChosen.subscribe(skill => chosen.push(skill));
    const buttons = fixture.nativeElement.querySelectorAll('[data-market-skills] button') as NodeListOf<HTMLButtonElement>;

    expect(buttons[0].disabled).toBeTrue();
    expect(buttons[1].getAttribute('aria-label')).toBe('Filtrer sur Kafka (5 offres)');
    buttons[1].click();

    expect(chosen).toEqual(['Kafka']);
  });

  it('fetches again for other filters only, and retries after a failure', () => {
    const fixture = create();

    fixture.componentRef.setInput('query', { ...defaultOfferQuery(), page: 3, sort: 'RATE' });
    fixture.detectChanges();
    expect(service.market).toHaveBeenCalledTimes(1);
    service.market.and.returnValue(throwError(() => new Error('down')));
    fixture.componentRef.setInput('query', { ...defaultOfferQuery(), q: 'java' });
    fixture.detectChanges();

    expect(service.market).toHaveBeenCalledTimes(2);
    expect(service.market.calls.mostRecent().args[0].q).toBe('java');
    expect(text(fixture, '[role="alert"]')).toContain("L'aperçu du marché n'a pas pu être chargé.");
    service.market.and.returnValue(of(market({ offers: 0 })));
    (fixture.nativeElement.querySelector('[role="alert"] button') as HTMLButtonElement).click();

    expect(text(fixture, '[data-market-empty]')).toBe('Aucune offre à décrire avec ces filtres.');
  });

  it('invites to set a target rate and copes with offers stating nothing', () => {
    const fixture = create(defaultOfferQuery(), of(market({
      rates: { stated: 0 }, rateBins: [], remote: { remote: 0, hybrid: 0, onsite: 0, unstated: 42 }, skills: [], intermediaries: []
    })));

    expect(text(fixture, '[data-kpi="median"]')).toBe('TJM médian –');
    expect(text(fixture, '[data-kpi="quartiles"]')).toBe('Moitié centrale des TJM –');
    expect(text(fixture, '[data-market-target]')).toContain('Indiquez un TJM cible dans votre profil');
    expect(fixture.nativeElement.querySelector('[data-market-target] a').getAttribute('href')).toBe('/profile');
    expect(text(fixture, '[data-market-rates]')).toContain("Aucune offre n'indique de TJM.");
    expect(text(fixture, '[data-market-remote]')).not.toContain('Offres hybrides');
    expect(text(fixture, '[data-market-skills]')).toContain('Non renseigné');
    expect(text(fixture, '[data-market-intermediaries]')).toContain('Non renseigné');
  });
});
