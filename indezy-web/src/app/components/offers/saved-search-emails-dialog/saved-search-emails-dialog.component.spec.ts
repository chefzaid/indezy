import { TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import en from '../../../../assets/i18n/en.json';
import { SavedOfferSearch, SavedSearchEmails } from '../../../models/offer.models';
import { LANGUAGE_STORAGE_KEY } from '../../../shared/locale/app-locale';
import { SavedSearchEmailsDialogComponent } from './saved-search-emails-dialog.component';

describe('SavedSearchEmailsDialogComponent', () => {
  let close: jasmine.Spy;

  beforeAll(() => registerLocaleData(localeFr));
  afterEach(() => localStorage.removeItem(LANGUAGE_STORAGE_KEY));

  function create(emails: Partial<SavedSearchEmails> = {}, emailsAvailable = true) {
    close = jasmine.createSpy('close');
    const search: SavedOfferSearch = {
      id: 4, name: 'Java', filters: {}, lastOpenedAt: '2026-10-18T08:00:00Z', newOffers: 0, emailsAvailable,
      emails: { frequency: 'OFF', hour: null, language: null, stoppedAt: null, ...emails }
    };
    TestBed.configureTestingModule({
      imports: [SavedSearchEmailsDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { search } },
        { provide: LOCALE_ID, useValue: 'en-US' }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(SavedSearchEmailsDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('starts from the search\'s emails and closes with a daily one at its hour in the app\'s language', () => {
    localStorage.setItem(LANGUAGE_STORAGE_KEY, 'en');
    const fixture = create({ frequency: 'INSTANT' });
    const dialog = fixture.componentInstance;
    expect(dialog.frequency).toBe('INSTANT');
    expect(dialog.hour).toBe(8);

    dialog.frequency = 'DAILY';
    dialog.hour = 18;
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Paris time');
    dialog.save();

    expect(close).toHaveBeenCalledOnceWith({ frequency: 'DAILY', hour: 18, language: 'EN' });
  });

  it('sends at once without an hour, in French by default', () => {
    const dialog = create().componentInstance;

    dialog.frequency = 'INSTANT';
    dialog.save();

    expect(close).toHaveBeenCalledOnceWith({ frequency: 'INSTANT', language: 'FR' });
  });

  it('tells when sending stopped after 60 unopened days, and when the server sends no email', () => {
    const stopped = create({ stoppedAt: '2026-10-01T08:00:00Z' });
    expect(stopped.nativeElement.querySelector('[data-emails-stopped]').textContent).toContain('October 1, 2026');
    TestBed.resetTestingModule();

    const unavailable = create({}, false);
    const dialog = unavailable.componentInstance;
    expect(unavailable.nativeElement.querySelector('[data-emails-unavailable]').textContent).toContain('does not send emails yet');
    dialog.frequency = 'DAILY';
    expect(dialog.canSave).toBeFalse();
    dialog.save();
    expect(close).not.toHaveBeenCalled();
    // Turning them off always works.
    dialog.frequency = 'OFF';
    dialog.save();
    expect(close).toHaveBeenCalledOnceWith({ frequency: 'OFF', language: 'FR' });
  });

  it('closes with nothing when cancelled', () => {
    const dialog = create().componentInstance;

    dialog.cancel();

    expect(close).toHaveBeenCalledOnceWith();
  });
});
