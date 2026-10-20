import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferSiteHealth, OfferSiteHealthEntry } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferSitesComponent } from './offer-sites.component';

describe('OfferSitesComponent', () => {
  let service: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;

  /** A moment that many minutes away, a quarter of a minute further so the time a test takes does not change its words. */
  const minutes = (count: number): string => new Date(Date.now() + (count + Math.sign(count) / 4) * 60_000).toISOString();

  function site(overrides: Partial<OfferSiteHealthEntry> = {}): OfferSiteHealthEntry {
    return {
      site: 'FREE_WORK', name: 'Free-Work', homeUrl: 'https://www.free-work.com', status: 'OK', lastRunAt: minutes(-20),
      lastSuccessAt: minutes(-20), nextRunAt: minutes(40), consecutiveFailures: 0, lastFound: 120, lastNew: 4, collectedDay: 40,
      collectedWeek: 210, openOffers: 812, newestOfferAt: minutes(-60), excluded: false, ...overrides
    };
  }

  function health(overrides: Partial<OfferSiteHealth> = {}): OfferSiteHealth {
    return {
      sites: [
        site({ site: 'YEETS', name: 'Yeets', homeUrl: 'https://www.yeets.fr', status: 'FAILING', consecutiveFailures: 3, lastSuccessAt: null,
          lastError: 'SiteResponseException: 503', lastFound: null, newestOfferAt: minutes(-60 * 24 * 45), excluded: true }),
        site(),
        site({ site: 'LEHIBOU', name: 'LeHibou', status: 'RUNNING', nextRunAt: minutes(-1) }),
        site({ site: 'KOINO', name: 'Koïno', status: 'WAITING', lastRunAt: null, lastSuccessAt: null, nextRunAt: null, lastFound: null,
          newestOfferAt: null, refreshAvailableAt: minutes(10) })
      ],
      leftOut: [{ site: 'LINKEDIN', name: 'LinkedIn', homeUrl: 'https://www.linkedin.com', kind: 'OTHER' },
        { site: 'MALT', name: 'Malt', homeUrl: 'https://www.malt.fr', kind: 'MATCHING', access: 'PROFILE' }],
      refreshEnabled: true,
      ...overrides
    };
  }

  function create(result: Observable<OfferSiteHealth> = of(health())): ComponentFixture<OfferSitesComponent> {
    registerLocaleData(localeFr);
    service = jasmine.createSpyObj('OfferService', ['siteHealth', 'setSiteExcluded', 'refreshSite', 'visitSite', 'clearSiteVisit']);
    service.siteHealth.and.returnValue(result);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [OfferSitesComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: OfferService, useValue: service },
        { provide: NotificationService, useValue: notifications }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferSitesComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferSitesComponent>, selector: string): string {
    fixture.detectChanges();
    return ((fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement).innerText.replace(/\s+/g, ' ').trim();
  }

  function button(fixture: ComponentFixture<OfferSitesComponent>, siteId: string): HTMLButtonElement {
    fixture.detectChanges();
    return fixture.nativeElement.querySelector(`[data-site="${siteId}"] [data-refresh]`) as HTMLButtonElement;
  }

  it('describes how each board is read, and the boards left out', () => {
    const fixture = create();

    expect(text(fixture, '[data-sites-summary]')).toBe('4 sites lus pour le fil · 1 en échec');
    expect(text(fixture, '[data-site="YEETS"] [data-status]')).toBe('En échec');
    expect(text(fixture, '[data-site="YEETS"]')).toContain("3 lectures échouées d'affilée");
    expect(text(fixture, '[data-site="YEETS"] [data-fact="last-success"]')).toBe('Jamais');
    expect(text(fixture, '[data-site="YEETS"] [data-fact="newest"]')).toContain('rien de neuf depuis un mois');
    expect(text(fixture, '[data-site="YEETS"] .last-error')).toContain('Dernière erreur');
    expect(text(fixture, '[data-site="YEETS"]')).toContain('Ses offres sont écartées de votre fil');
    expect((fixture.nativeElement.querySelector('[data-site="YEETS"] [data-include] button') as HTMLButtonElement)
      .getAttribute('aria-checked')).toBe('false');
    expect(text(fixture, '[data-site="FREE_WORK"] [data-fact="last-success"]')).toBe('il y a 20 minutes');
    expect(text(fixture, '[data-site="FREE_WORK"] [data-fact="next-run"]')).toBe('dans 40 minutes');
    expect(text(fixture, '[data-site="FREE_WORK"] [data-fact="collected"]')).toBe('40 en 24 h · 210 en 7 jours');
    expect(text(fixture, '[data-site="FREE_WORK"] [data-fact="open"]')).toBe('812');
    expect(text(fixture, '[data-site="FREE_WORK"] [data-fact="last-run"]')).toBe('120 offres, dont 4 nouvelles');
    expect(text(fixture, '[data-site="LEHIBOU"] [data-fact="next-run"]')).toBe('Imminente');
    expect(text(fixture, '[data-site="KOINO"] [data-fact="newest"]')).toBe('Aucune');
    expect(text(fixture, '[data-left-out="LINKEDIN"]')).toContain('LinkedIn son robots.txt');
    expect(fixture.nativeElement.querySelector('[data-left-out="LINKEDIN"] a').getAttribute('href')).toBe('https://www.linkedin.com');
    expect(fixture.nativeElement.querySelector('[data-left-out="MALT"]')).toBeNull();
    expect(text(fixture, '#sites-to-visit [data-visit="MALT"]')).toContain('créez votre profil');
  });

  it('lets a board be read when nobody did in the last quarter of an hour', () => {
    const fixture = create();

    expect(button(fixture, 'FREE_WORK').disabled).toBeFalse();
    expect(button(fixture, 'LEHIBOU').disabled).toBeTrue();
    expect(text(fixture, '[data-site="LEHIBOU"] [data-refresh-hint]')).toBe('Une lecture est en cours.');
    expect(button(fixture, 'KOINO').disabled).toBeTrue();
    expect(text(fixture, '[data-site="KOINO"] [data-refresh-hint]')).toBe('Lecture possible à nouveau dans 10 minutes.');
    expect(button(fixture, 'FREE_WORK').getAttribute('aria-label')).toBe('Lire Free-Work maintenant');
  });

  it('reads a board on request and tells when it can be asked for again', () => {
    const fixture = create();
    service.refreshSite.and.returnValue(of({ site: 'FREE_WORK', refreshAvailableAt: minutes(15) }));

    button(fixture, 'FREE_WORK').click();

    expect(service.refreshSite).toHaveBeenCalledWith('FREE_WORK');
    expect(notifications.success).toHaveBeenCalledWith('offers.sites.notifications.refreshStarted', 6000, { site: 'Free-Work' });
    expect(button(fixture, 'FREE_WORK').disabled).toBeTrue();
    expect(text(fixture, '[data-site="FREE_WORK"] [data-refresh-hint]')).toBe('Lecture possible à nouveau dans 15 minutes.');
  });

  it('explains a refused reading', () => {
    const fixture = create();
    service.refreshSite.and.returnValue(throwError(() => new HttpErrorResponse({ status: 429,
      headers: new HttpHeaders({ 'Retry-After': '540' }) })));

    button(fixture, 'FREE_WORK').click();

    expect(notifications.error).toHaveBeenCalledWith('offers.sites.errors.refreshTooSoon', 6000,
      { site: 'Free-Work', when: 'dans 9 minutes' });
    expect(button(fixture, 'FREE_WORK').disabled).toBeTrue();

    const component = fixture.componentInstance;
    service.refreshSite.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.refresh(component.health!.sites[3]);
    expect(notifications.error).toHaveBeenCalledWith('offers.sites.refreshOff');
    service.refreshSite.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.refresh(component.health!.sites[3]);
    expect(notifications.error).toHaveBeenCalledWith('offers.sites.errors.refresh', 5000, { site: 'Koïno' });
    expect(component.refreshing.size).toBe(0);
  });

  it('keeps a board out of the feed and undoes the switch when saving fails', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    service.setSiteExcluded.and.returnValue(of({ site: 'FREE_WORK', excluded: true }));

    component.setIncluded(component.health!.sites[1], false);

    expect(service.setSiteExcluded).toHaveBeenCalledWith('FREE_WORK', true);
    expect(component.health!.sites[1].excluded).toBeTrue();
    expect(notifications.success).toHaveBeenCalledWith('offers.sites.notifications.excluded', 3000, { site: 'Free-Work' });
    service.setSiteExcluded.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.setIncluded(component.health!.sites[0], true);

    expect(component.health!.sites[0].excluded).toBeTrue();
    expect(notifications.error).toHaveBeenCalledWith('offers.sites.errors.exclusion');
    expect(component.saving.size).toBe(0);
  });

  it('says when reading on request is off, and retries a failed load', () => {
    const fixture = create(throwError(() => new HttpErrorResponse({ status: 500 })));

    expect(text(fixture, '[role="alert"]')).toContain("Les sites d'offres n'ont pas pu être chargés.");
    service.siteHealth.and.returnValue(of(health({ refreshEnabled: false })));
    (fixture.nativeElement.querySelector('[role="alert"] button') as HTMLButtonElement).click();

    expect(text(fixture, '.limit-note')).toContain('La lecture à la demande est désactivée sur ce serveur.');
    expect(button(fixture, 'FREE_WORK').disabled).toBeTrue();
    expect(fixture.nativeElement.querySelector('[data-refresh-hint]')).toBeNull();
  });
});
