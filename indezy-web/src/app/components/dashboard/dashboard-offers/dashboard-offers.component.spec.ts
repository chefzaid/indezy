import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { Offer, OfferPage } from '../../../models/offer.models';
import { OfferService } from '../../../services/offer/offer.service';
import { NetworkService } from '../../../services/network/network.service';
import { NetworkPost } from '../../../models/network.models';
import { DashboardOffersComponent } from './dashboard-offers.component';

describe('DashboardOffersComponent', () => {
  let missionsSet = false;
  afterEach(() => missionsSet = false);
  let service: jasmine.SpyObj<OfferService>;
  let network: jasmine.SpyObj<NetworkService>;

  function offer(id: number, overrides: Partial<Offer> = {}): Offer {
    return {
      id, site: 'FREE_WORK', siteName: 'Free-Work', url: `https://www.free-work.com/${id}`, title: `Mission ${id}`, skills: [],
      publishedAt: '2026-10-19T08:00:00Z', datePrecision: 'EXACT', expired: false, saved: false, hidden: false, seen: false, ...overrides
    };
  }

  function page(content: Offer[], matchAvailable: boolean): OfferPage {
    return { content, page: 0, size: 5, totalElements: content.length, totalPages: 1, distanceAvailable: false, matchAvailable };
  }

  function create(...answers: ReturnType<OfferService['searchQuietly']>[]): ComponentFixture<DashboardOffersComponent> {
    registerLocaleData(localeFr);
    service = jasmine.createSpyObj('OfferService', ['searchQuietly', 'updateState']);
    service.searchQuietly.and.returnValues(...answers);
    service.updateState.and.returnValue(of({ offerId: 1, saved: false, hidden: false, seen: true }));
    if (!missionsSet) {
      // Away from the network: no mission joins the offers.
      network = jasmine.createSpyObj('NetworkService', ['missions', 'missionMatches']);
      network.missions.and.returnValue(throwError(() => new Error('not a member')));
    }
    TestBed.configureTestingModule({
      imports: [DashboardOffersComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: LOCALE_ID, useValue: 'fr-FR' }, { provide: OfferService, useValue: service },
        { provide: NetworkService, useValue: network }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(DashboardOffersComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<DashboardOffersComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).innerText.replace(/\s+/g, ' ');
  }

  it('shows the unseen offers that match the profile best', () => {
    const fixture = create(of(page([offer(1, { company: 'Exemple Conseil', rateMin: 550, rateMax: 650, city: 'Courbevoie', departmentCode: '92',
      countryCode: 'FR', workMode: 'HYBRID', remoteDaysPerWeek: 2, durationMonths: 6, startAsap: true,
      excerpt: 'Au sein de la DSI, vous concevez les API de paiement…', skills: ['Kafka', 'Java', 'Spring'],
      match: { score: 80, skills: ['Java'] } })], true)));

    expect(service.searchQuietly).toHaveBeenCalledWith(jasmine.objectContaining({ sort: 'MATCH', unseenOnly: true, size: 5 }));
    expect(text(fixture)).toContain('Recommandées pour vous');
    expect(text(fixture)).toContain('Exemple Conseil');
    const row = fixture.nativeElement.querySelector('[data-dashboard-offer="1"]') as HTMLElement;
    expect(row.querySelector('[data-offer-rate]')!.textContent!.replace(/\s/g, ' ')).toContain('550 € – 650 €/jour');
    expect(text(fixture)).toContain('Courbevoie (92)');
    expect(text(fixture)).toContain('2 j/sem. de télétravail');
    expect(text(fixture)).toContain('6 mois');
    expect(row.querySelector('[data-offer-excerpt]')!.textContent).toContain('API de paiement');
    // The skills the profile names come first, set apart.
    const skills = Array.from(row.querySelectorAll('.skill'));
    expect(skills.map(skill => skill.textContent!.trim())).toEqual(['Java', 'Kafka', 'Spring']);
    expect(skills[0].classList).toContain('matched');
    expect(skills[1].classList).not.toContain('matched');
    expect(text(fixture)).toContain('80 %');
    expect(fixture.nativeElement.querySelector('.feed-link').getAttribute('href')).toBe('/offers?sort=MATCH&unseenOnly=true');
    expect(fixture.nativeElement.querySelector('[data-profile-hint]')).toBeNull();
  });

  it('shows the newest unseen offers when the profile names no skills, each linked to its page', () => {
    const fixture = create(of(page([], false)), of(page([offer(2), offer(3)], false)));

    expect(service.searchQuietly.calls.mostRecent().args[0].sort).toBe('NEWEST');
    expect(text(fixture)).toContain('Nouvelles offres');
    expect(text(fixture)).toContain('Ajoutez vos compétences à votre profil');
    const link = fixture.nativeElement.querySelector('[data-dashboard-offer="2"] a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/offers/2');
    expect(link.target).toBe('');
    expect(fixture.nativeElement.querySelector('.feed-link').getAttribute('href')).toBe('/offers?unseenOnly=true');
  });

  it('says when nothing is new and hides itself when the feed cannot be read', () => {
    const empty = create(of(page([], true)));
    expect(text(empty)).toContain('Aucune nouvelle offre depuis votre dernier passage');
    TestBed.resetTestingModule();

    const failed = create(throwError(() => new Error('down')));
    expect(failed.nativeElement.querySelector('section')).toBeNull();
  });

  it('ranks the network missions fitting the profile with the offers, an offer moved by its rate against the market', () => {
    network = jasmine.createSpyObj('NetworkService', ['missions', 'missionMatches']);
    missionsSet = true;
    const mission = (id: number) => ({ id, kind: 'MISSION', author: { firstName: 'Léo', lastName: 'Martin' },
      details: { role: `Lead ${id}`, rateMin: 600, rateMax: 700, city: 'Lyon', remoteDays: 2, start: null, client: null, stack: ['Kafka'] } }) as unknown as NetworkPost;
    network.missions.and.returnValue(of({ posts: [mission(7), mission(8)], page: 0, more: false }));
    network.missionMatches.and.returnValue(of({ 7: { score: 80, skills: ['Kafka'] }, 8: { score: 20, skills: [] } }));
    const fixture = create(of(page([offer(1, { match: { score: 78, skills: ['Java'] }, rateMarket: { percent: 20, median: 600, offers: 30, skill: 'Java' } }),
      offer(2, { match: { score: 79, skills: ['Java'] } })], true)));
    const component = fixture.componentInstance;

    expect(network.missions).toHaveBeenCalledWith(0, { bestMatch: true, hideTaken: true });
    expect(network.missionMatches).toHaveBeenCalledWith([7, 8]);
    // 78 + 4 for a rate 20 % above the median, then the mission's 80, then 79; the mission at 20 is left out.
    expect(component.items.map(item => item.kind === 'offer' ? `offer ${item.offer.id}` : `mission ${item.post.id}`))
      .toEqual(['offer 1', 'mission 7', 'offer 2']);
    const row = (fixture.nativeElement as HTMLElement).querySelector('[data-dashboard-mission="7"]')!;
    expect(row.textContent).toContain('Lead 7');
    expect(row.querySelector('[data-mission-from]')!.textContent).toContain('Léo Martin');
    expect(component.missionRate(mission(7))).toContain('600');
  });
});
