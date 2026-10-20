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
import { DashboardOffersComponent } from './dashboard-offers.component';

describe('DashboardOffersComponent', () => {
  let service: jasmine.SpyObj<OfferService>;

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
    TestBed.configureTestingModule({
      imports: [DashboardOffersComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: LOCALE_ID, useValue: 'fr-FR' }, { provide: OfferService, useValue: service }]
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
      countryCode: 'FR', match: { score: 80, skills: ['Java'] } })], true)));

    expect(service.searchQuietly).toHaveBeenCalledWith(jasmine.objectContaining({ sort: 'MATCH', unseenOnly: true, size: 5 }));
    expect(text(fixture)).toContain('Vos meilleures nouvelles missions');
    expect(text(fixture)).toContain('Exemple Conseil');
    expect(text(fixture).replace(/\s/g, ' ')).toContain('550 € – 650 €');
    expect(text(fixture)).toContain('Courbevoie (92)');
    expect(text(fixture)).toContain('80 %');
    expect(fixture.nativeElement.querySelector('.feed-link').getAttribute('href')).toBe('/offers?sort=MATCH&unseenOnly=true');
    expect(fixture.nativeElement.querySelector('[data-profile-hint]')).toBeNull();
  });

  it('shows the newest unseen offers when the profile names no skills, and marks an opened one seen', () => {
    const fixture = create(of(page([], false)), of(page([offer(2), offer(3)], false)));

    expect(service.searchQuietly.calls.mostRecent().args[0].sort).toBe('NEWEST');
    expect(text(fixture)).toContain('Nouvelles missions');
    expect(text(fixture)).toContain('Ajoutez vos compétences à votre profil');
    const link = fixture.nativeElement.querySelector('[data-dashboard-offer="2"] a') as HTMLAnchorElement;
    link.addEventListener('click', event => event.preventDefault());
    link.click();

    fixture.detectChanges();

    expect(service.updateState).toHaveBeenCalledWith(2, { seen: true });
    expect(fixture.nativeElement.querySelectorAll('[data-dashboard-offer]').length).toBe(1);
    expect(fixture.nativeElement.querySelector('.feed-link').getAttribute('href')).toBe('/offers?unseenOnly=true');
  });

  it('says when nothing is new and hides itself when the feed cannot be read', () => {
    const empty = create(of(page([], true)));
    expect(text(empty)).toContain('Aucune nouvelle mission depuis votre dernier passage');
    TestBed.resetTestingModule();

    const failed = create(throwError(() => new Error('down')));
    expect(failed.nativeElement.querySelector('section')).toBeNull();
  });
});
