import { TestBed, fakeAsync, tick, discardPeriodicTasks } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { OfferService } from './offer.service';
import { OFFER_ALERTS_REFRESH_MS, OfferAlertsService } from './offer-alerts.service';

describe('OfferAlertsService', () => {
  let offers: jasmine.SpyObj<OfferService>;
  let alerts: OfferAlertsService;
  let counts: number[];

  beforeEach(() => {
    offers = jasmine.createSpyObj('OfferService', ['alerts']);
    TestBed.configureTestingModule({ providers: [{ provide: OfferService, useValue: offers }] });
    alerts = TestBed.inject(OfferAlertsService);
    counts = [];
    alerts.newOffers$.subscribe(count => counts.push(count));
  });

  it('counts at once, then every few minutes, until stopped', fakeAsync(() => {
    offers.alerts.and.returnValues(of({ newOffers: 3 }), of({ newOffers: 5 }), of({ newOffers: 7 }));

    alerts.start();
    alerts.start();
    tick(OFFER_ALERTS_REFRESH_MS);
    alerts.stop();
    tick(OFFER_ALERTS_REFRESH_MS);

    expect(offers.alerts).toHaveBeenCalledTimes(3);
    expect(counts).toEqual([0, 3, 5, 7, 0]);
    discardPeriodicTasks();
  }));

  it('keeps the count when counting fails', () => {
    offers.alerts.and.returnValues(of({ newOffers: 2 }), throwError(() => new Error('offline')));

    alerts.refresh();
    alerts.refresh();

    expect(counts).toEqual([0, 2]);
  });
});
