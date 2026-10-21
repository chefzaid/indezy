import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { HttpErrorResponse } from '@angular/common/http';
import { MatDialog } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import fr from '../../../../assets/i18n/fr.json';
import { Offer } from '../../../models/offer.models';
import { NotificationService } from '../../../services/notification/notification.service';
import { OfferService } from '../../../services/offer/offer.service';
import { OfferShareService } from '../../../services/offer/offer-share.service';
import { OfferPageComponent } from './offer-page.component';

describe('OfferPageComponent', () => {
  const offer: Offer = {
    id: 7, site: 'FREE_WORK', siteName: 'Free-Work', url: 'https://www.free-work.com/fr/tech-it/job-mission/java/7',
    title: 'Développeur Java Spring', company: 'Exemple Conseil', excerpt: 'Refonte…', description: 'Refonte complète du SI bancaire.',
    skills: ['Java'], publishedAt: new Date(Date.now() - 3_600_000).toISOString(), datePrecision: 'EXACT', expired: false, saved: false,
    hidden: false, seen: false
  };
  let offers: jasmine.SpyObj<OfferService>;
  let share: jasmine.SpyObj<OfferShareService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let params: BehaviorSubject<Record<string, string>>;

  function create(answer: Observable<Offer>, id = '7'): ComponentFixture<OfferPageComponent> {
    registerLocaleData(localeFr);
    offers = jasmine.createSpyObj('OfferService', ['get', 'updateState']);
    share = jasmine.createSpyObj('OfferShareService', ['copyLink']);
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    offers.get.and.returnValue(answer);
    offers.updateState.and.returnValue(of({ offerId: 7, saved: true, hidden: false, seen: true }));
    params = new BehaviorSubject<Record<string, string>>({ id });
    TestBed.configureTestingModule({
      imports: [OfferPageComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: ActivatedRoute, useValue: { paramMap: params.pipe(source => new Observable(subscriber =>
          source.subscribe(value => subscriber.next(convertToParamMap(value))))) } },
        { provide: OfferService, useValue: offers },
        { provide: OfferShareService, useValue: share },
        { provide: NotificationService, useValue: notifications },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(undefined) }) } }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferPageComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<OfferPageComponent>): string {
    return (fixture.nativeElement as HTMLElement).textContent!.replace(/\s+/g, ' ');
  }

  it('shows the whole offer, marks it seen and copies its link', () => {
    const fixture = create(of(offer));

    expect(offers.get).toHaveBeenCalledWith(7);
    expect(offers.updateState).toHaveBeenCalledWith(7, { seen: true });
    expect(text(fixture)).toContain('Refonte complète du SI bancaire.');
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Développeur Java Spring');
    expect(fixture.nativeElement.querySelector('.details-button')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-select]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[data-offer-expired]')).toBeNull();
    (fixture.nativeElement.querySelector('[data-copy-offer-link]') as HTMLButtonElement).click();
    expect(share.copyLink).toHaveBeenCalledWith(7);
    expect(text(fixture)).toContain('Ouvrir sur Free-Work');
  });

  it('acts on the offer at once and puts it back when the server refuses', () => {
    const fixture = create(of(offer));
    const component = fixture.componentInstance;

    component.actions.toggleSaved(component.offer!);
    expect(component.offer!.saved).toBeTrue();

    offers.updateState.and.returnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.actions.toggleHidden(component.offer!);
    fixture.detectChanges();
    expect(component.offer!.hidden).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('offers.errors.state');

    offers.updateState.and.returnValue(of({ offerId: 7, saved: true, hidden: true, seen: true }));
    component.actions.toggleHidden(component.offer!);
    fixture.detectChanges();
    // Hidden, it stays on its page, which says so.
    expect(fixture.nativeElement.querySelector('[data-offer-hidden]')).not.toBeNull();
    component.patch(99, { saved: false });
    expect(component.offer!.saved).toBeTrue();
  });

  it('lists every change its site made', () => {
    const fixture = create(of({ ...offer, history: { changes: [
      { field: 'START', after: 'ASAP', changedAt: '2026-10-20T08:00:00Z' },
      { field: 'RATE', before: '|500', after: '|550', changedAt: '2026-10-18T08:00:00Z' }] } }));

    expect(Array.from(fixture.nativeElement.querySelectorAll('[data-offer-history-list] li') as NodeListOf<HTMLElement>)
      .map(item => item.textContent!.replace(/\s+/g, ' ').trim()))
      .toEqual(['Démarre désormais au plus tôt, le 20 oct. 2026', 'TJM relevé de 500 € à 550 €, le 18 oct. 2026']);
  });

  it('says when the offer has expired', () => {
    const expiring = create(of({ ...offer, expired: true, expiresAt: '2026-10-15T10:00:00Z' }));
    expect(expiring.nativeElement.querySelector('[data-offer-expired]').textContent).toContain('Cette offre a expiré le 15 octobre 2026');

    TestBed.resetTestingModule();
    const expired = create(of({ ...offer, expired: true }));
    expect(expired.nativeElement.querySelector('[data-offer-expired]').textContent).toContain('Cette offre a expiré :');
  });

  it('explains an offer no longer in the feed, and an address naming no offer', () => {
    const gone = create(throwError(() => new HttpErrorResponse({ status: 404 })));
    expect(gone.nativeElement.querySelector('[data-offer-gone]')).not.toBeNull();
    expect(text(gone)).toContain('Les offres quittent le fil 90 jours après leur publication');

    TestBed.resetTestingModule();
    const named = create(of(offer), 'abc');
    expect(offers.get).not.toHaveBeenCalled();
    expect(named.nativeElement.querySelector('[data-offer-gone]')).not.toBeNull();
  });

  it('offers to try again after a failure, and follows the address to another offer', () => {
    const fixture = create(throwError(() => new HttpErrorResponse({ status: 500 })));
    expect(text(fixture)).toContain('L\'offre n\'a pas pu être chargée.');

    offers.get.and.returnValue(of(offer));
    (fixture.nativeElement.querySelector('.state-card button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(text(fixture)).toContain('Refonte complète du SI bancaire.');

    offers.get.and.returnValue(of({ ...offer, id: 8, title: 'Architecte cloud', seen: true }));
    params.next({ id: '8' });
    fixture.detectChanges();
    expect(offers.get).toHaveBeenCalledWith(8);
    expect(fixture.nativeElement.querySelector('h1').textContent).toContain('Architecte cloud');
  });
});
