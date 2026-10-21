import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { OfferLeftOutSite } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferSitesToVisitComponent } from './offer-sites-to-visit.component';

describe('OfferSitesToVisitComponent', () => {
  let service: jasmine.SpyObj<OfferService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const twoDaysAgo = new Date(Date.now() - 2 * 86_400_000 - 60_000).toISOString();

  const sites: OfferLeftOutSite[] = [
    { site: 'HITECHPROS', name: 'HiTechPros', homeUrl: 'https://www.hitechpros.com', kind: 'VISIT', access: 'MEMBERSHIP' },
    { site: 'OPTEAMIS', name: 'Opteamis', homeUrl: 'https://www.opteamis.com', kind: 'VISIT', access: 'SIGN_IN', visitedAt: twoDaysAgo },
    { site: 'MALT', name: 'Malt', homeUrl: 'https://www.malt.fr', kind: 'MATCHING', access: 'PROFILE' },
    { site: 'LINKEDIN', name: 'LinkedIn', homeUrl: 'https://www.linkedin.com', kind: 'OTHER' }
  ];

  function create(): ComponentFixture<OfferSitesToVisitComponent> {
    registerLocaleData(localeFr);
    service = jasmine.createSpyObj('OfferService', ['visitSite', 'clearSiteVisit']);
    notifications = jasmine.createSpyObj('NotificationService', ['error']);
    TestBed.configureTestingModule({
      imports: [OfferSitesToVisitComponent, TranslateModule.forRoot()],
      providers: [{ provide: LOCALE_ID, useValue: 'fr-FR' }, { provide: OfferService, useValue: service },
        { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferSitesToVisitComponent);
    fixture.componentRef.setInput('sites', sites);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferSitesToVisitComponent>, selector: string): string {
    fixture.detectChanges();
    return ((fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement).innerText.replace(/\s+/g, ' ').trim();
  }

  it('groups the sites to see in person and the matching services, with what each takes', () => {
    const fixture = create();
    const headings = Array.from(fixture.nativeElement.querySelectorAll('h3') as NodeListOf<HTMLElement>).map(heading => heading.innerText);

    expect(headings).toEqual(['Missions à consulter en personne', 'Services de mise en relation : créez votre profil une fois, ils vous appellent']);
    expect(text(fixture, '[data-visit="HITECHPROS"] [data-access]')).toBe('Abonnement payant');
    expect(text(fixture, '[data-visit="OPTEAMIS"]')).toContain('Consulté avant-hier');
    expect(text(fixture, '[data-visit="MALT"]')).toContain('Pas encore consulté');
    expect(fixture.nativeElement.querySelector('[data-visit="LINKEDIN"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-visit="MALT"] a').getAttribute('href')).toBe('https://www.malt.fr');
  });

  it('records a visit when the site is opened or marked, and forgets it', () => {
    const fixture = create();
    service.visitSite.and.returnValue(of({ site: 'MALT', visitedAt: new Date().toISOString() }));
    service.clearSiteVisit.and.returnValue(of({ site: 'OPTEAMIS', visitedAt: null }));

    const link = fixture.nativeElement.querySelector('[data-visit="MALT"] a') as HTMLAnchorElement;
    link.addEventListener('click', event => event.preventDefault());
    link.click();
    (fixture.nativeElement.querySelector('[data-visit="OPTEAMIS"] button') as HTMLButtonElement).click();

    expect(service.visitSite).toHaveBeenCalledWith('MALT');
    expect(text(fixture, '[data-visit="MALT"] [data-visited]')).toContain('Consulté');
    expect(service.clearSiteVisit).toHaveBeenCalledWith('OPTEAMIS');
    expect(text(fixture, '[data-visit="OPTEAMIS"]')).toContain('Pas encore consulté');
    expect(fixture.nativeElement.querySelector('[data-visit="HITECHPROS"] button').getAttribute('aria-label'))
      .toBe('Marquer HiTechPros comme consulté');
  });

  it('puts the former visit back when saving fails', () => {
    const fixture = create();
    service.clearSiteVisit.and.returnValue(throwError(() => new Error('down')));

    fixture.componentInstance.clear(sites[1]);

    expect(text(fixture, '[data-visit="OPTEAMIS"]')).toContain('Consulté avant-hier');
    expect(notifications.error).toHaveBeenCalledWith('offers.sites.visit.error');
    expect(fixture.componentInstance.saving.size).toBe(0);
  });
});
