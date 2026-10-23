import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import fr from '../../../assets/i18n/fr.json';
import { OfferService } from '../../services/offer/offer.service';
import { EmailsUnsubscribeComponent } from './emails-unsubscribe.component';

describe('EmailsUnsubscribeComponent', () => {
  let offers: jasmine.SpyObj<OfferService>;

  function create(fragment: string | null): ComponentFixture<EmailsUnsubscribeComponent> {
    offers = jasmine.createSpyObj('OfferService', ['publicSearchEmails', 'unsubscribeSearchEmails']);
    offers.publicSearchEmails.and.returnValue(of({ searchName: 'Java à Lyon', frequency: 'DAILY', language: 'FR' }));
    offers.unsubscribeSearchEmails.and.returnValue(of({ searchName: 'Java à Lyon', frequency: 'OFF', language: 'FR' }));
    TestBed.configureTestingModule({
      imports: [EmailsUnsubscribeComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: OfferService, useValue: offers },
        { provide: ActivatedRoute, useValue: { snapshot: { fragment } } }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    return TestBed.createComponent(EmailsUnsubscribeComponent);
  }

  function text(fixture: ComponentFixture<EmailsUnsubscribeComponent>): string {
    fixture.detectChanges();
    return fixture.nativeElement.textContent.replace(/\s+/g, ' ');
  }

  it('names the search and stops its emails only when asked', () => {
    const fixture = create('tok3n');

    expect(text(fixture)).toContain('Vous recevez par email les nouvelles offres de la recherche « Java à Lyon »');
    expect(offers.publicSearchEmails).toHaveBeenCalledOnceWith('tok3n');
    expect(offers.unsubscribeSearchEmails).not.toHaveBeenCalled();

    fixture.nativeElement.querySelector('[data-unsubscribe]').click();

    expect(offers.unsubscribeSearchEmails).toHaveBeenCalledOnceWith('tok3n');
    expect(text(fixture)).toContain('Vous ne recevez plus par email les nouvelles offres de « Java à Lyon »');
    expect(fixture.nativeElement.querySelector('[data-unsubscribed]')).not.toBeNull();
  });

  it('says so when the emails are already stopped or could not be stopped', () => {
    const fixture = create('tok3n');
    offers.publicSearchEmails.and.returnValue(of({ searchName: 'Java', frequency: 'OFF', language: 'FR' }));
    expect(text(fixture)).toContain('Emails arrêtés');

    TestBed.resetTestingModule();
    const failing = create('tok3n');
    offers.unsubscribeSearchEmails.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    text(failing);
    failing.nativeElement.querySelector('[data-unsubscribe]').click();
    expect(text(failing)).toContain('Les emails n\'ont pas pu être arrêtés');
    expect(failing.nativeElement.querySelector('[data-unsubscribe]').disabled).toBeFalse();
  });

  it('tells a link without a token or with an unknown one is no longer valid', () => {
    const empty = create(null);
    expect(text(empty)).toContain('Ce lien n\'est plus valide');
    expect(offers.publicSearchEmails).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    const unknown = create('gone');
    offers.publicSearchEmails.and.returnValue(throwError(() => new HttpErrorResponse({ status: 404 })));
    expect(text(unknown)).toContain('Ce lien n\'est plus valide');
  });
});
