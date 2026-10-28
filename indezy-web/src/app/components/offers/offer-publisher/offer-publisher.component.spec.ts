import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferPublisher } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferPublisherComponent } from './offer-publisher.component';

describe('OfferPublisherComponent', () => {
  const publisher: OfferPublisher = {
    key: 'exemple conseil', name: 'Exemple Conseil', openMissions: 4, medianRate: 600, medianOpenDays: 12, missions: 6, republished: 2,
    sites: [{ site: 'FREE_WORK', siteName: 'Free-Work', offers: 3 }, { site: 'COLLECTIVE', siteName: 'Collective', offers: 1 }],
    client: { id: 12, name: 'Exemple Conseil', intermediary: true },
    offers: [{ id: 7, title: 'Développeur Java', siteName: 'Free-Work', rateMin: 550, rateMax: 650, city: 'Lyon', publishedAt: '2026-10-20T08:00:00Z' }]
  };
  let offers: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  function create(answer: Observable<OfferPublisher>): ComponentFixture<OfferPublisherComponent> {
    registerLocaleData(localeFr);
    offers = jasmine.createSpyObj('OfferService', ['publisher', 'blockPublisher', 'unblockPublisher']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    offers.publisher.and.returnValue(answer);
    TestBed.configureTestingModule({
      imports: [OfferPublisherComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ name: 'Exemple Conseil' })) } },
        { provide: OfferService, useValue: offers },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferPublisherComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferPublisherComponent>, selector: string): string {
    return ((fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement).textContent!.replace(/\s+/g, ' ').trim();
  }

  it('shows what the publisher puts in the feed, with its client record', () => {
    const fixture = create(of(publisher));

    expect(offers.publisher).toHaveBeenCalledWith('Exemple Conseil');
    expect(text(fixture, 'h1 span')).toBe('Exemple Conseil');
    expect(text(fixture, '[data-publisher-kpi="missions"] dd')).toBe('4');
    expect(text(fixture, '[data-publisher-kpi="rate"] dd').replace(/\s/g, ' ')).toBe('600 €');
    expect(text(fixture, '[data-publisher-kpi="age"] dd')).toBe('12 jours');
    expect(text(fixture, '[data-publisher-kpi="republished"] dd')).toBe('2 missions sur 6');
    expect(text(fixture, '[data-publisher-client]')).toContain('Exemple Conseil dans vos clients (ESN)');
    expect((fixture.nativeElement.querySelector('[data-publisher-client]') as HTMLAnchorElement).getAttribute('href')).toBe('/clients/12');
    expect(text(fixture, '[data-publisher-offer="7"]').replace(/\s/g, ' ')).toContain('Free-Work · 550 € – 650 € · Lyon');
    expect((fixture.nativeElement.querySelector('[data-publisher-offer="7"] a') as HTMLAnchorElement).getAttribute('href'))
      .toBe('/offers/7');
  });

  it('hides its offers and brings them back', () => {
    const fixture = create(of({ ...publisher, client: undefined, medianRate: undefined, medianOpenDays: undefined }));
    expect(fixture.nativeElement.querySelector('[data-publisher-no-client]')).not.toBeNull();
    expect(text(fixture, '[data-publisher-kpi="rate"] dd')).toBe('Non précisé');
    offers.blockPublisher.and.returnValue(of({ id: 9, name: 'Exemple Conseil' }));

    (fixture.nativeElement.querySelector('[data-publisher-block]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(offers.blockPublisher).toHaveBeenCalledWith('Exemple Conseil');
    expect(fixture.nativeElement.querySelector('[data-publisher-blocked]')).not.toBeNull();

    offers.unblockPublisher.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    (fixture.nativeElement.querySelector('[data-publisher-block]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.block');
    offers.unblockPublisher.and.returnValue(of(undefined));
    (fixture.nativeElement.querySelector('[data-publisher-block]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(offers.unblockPublisher).toHaveBeenCalledWith(9);
    expect(fixture.nativeElement.querySelector('[data-publisher-blocked]')).toBeNull();
    expect(notifications.success).toHaveBeenCalledWith('profile.blockedPublishers.unblocked', 6000, { publisher: 'Exemple Conseil' });
  });

  it('explains a publisher without offers, and offers to try again after a failure', () => {
    const gone = create(throwError(() => new HttpErrorResponse({ status: 404 })));
    expect(gone.nativeElement.querySelector('[data-publisher-gone]')).not.toBeNull();

    TestBed.resetTestingModule();
    const failing = create(throwError(() => new HttpErrorResponse({ status: 500 })));
    offers.publisher.and.returnValue(of(publisher));
    (failing.nativeElement.querySelector('.state-card button') as HTMLButtonElement).click();
    failing.detectChanges();
    expect(text(failing, 'h1 span')).toBe('Exemple Conseil');
  });
});
