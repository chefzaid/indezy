import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import fr from '../../../../assets/i18n/fr.json';
import { Offer } from '../../../models/offer.models';
import { OfferApplyDialogComponent } from './offer-apply-dialog.component';

describe('OfferApplyDialogComponent', () => {
  let dialogRef: jasmine.SpyObj<MatDialogRef<OfferApplyDialogComponent>>;

  function create(offer: Partial<Offer> = {}): ComponentFixture<OfferApplyDialogComponent> {
    registerLocaleData(localeFr);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    TestBed.configureTestingModule({
      imports: [OfferApplyDialogComponent, TranslateModule.forRoot()],
      providers: [
        provideNativeDateAdapter(),
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { offer: { id: 7, title: 'Développeur Java', company: 'Exemple Conseil', siteName: 'Free-Work',
          ...offer } } }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(OfferApplyDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  function element(fixture: ComponentFixture<OfferApplyDialogComponent>, selector: string): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLElement;
  }

  function isoDay(date: Date): string {
    return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`;
  }

  it('records an application through the site today with a follow-up a week later', () => {
    const fixture = create();
    const text = (fixture.nativeElement as HTMLElement).innerText;

    expect(text).toContain('Développeur Java');
    expect(text).toContain('Exemple Conseil · Free-Work');
    expect(text).toContain("La mission rejoint votre pipeline à l'étape Contact");
    const inAWeek = new Date();
    inAWeek.setDate(inAWeek.getDate() + 7);
    expect(element(fixture, '[data-follow-up-day]').textContent).toContain(String(inAWeek.getDate()));
    element(fixture, '[data-confirm-apply]').click();

    expect(dialogRef.close).toHaveBeenCalledWith({ via: 'SITE_FORM', appliedOn: isoDay(new Date()), followUpInDays: 7 });
  });

  it('records a past application by phone without a follow-up', () => {
    const fixture = create({ importedProjectId: 40 });
    const component = fixture.componentInstance;
    const lastWeek = new Date();
    lastWeek.setDate(lastWeek.getDate() - 6);

    expect((fixture.nativeElement as HTMLElement).innerText).toContain('Son opportunité dans votre pipeline');
    component.form.patchValue({ via: 'PHONE', appliedOn: lastWeek, followUp: false });
    fixture.detectChanges();
    expect(element(fixture, '[data-follow-up-days]')).toBeNull();
    element(fixture, '[data-confirm-apply]').click();

    expect(dialogRef.close).toHaveBeenCalledWith({ via: 'PHONE', appliedOn: isoDay(lastWeek) });
  });

  it('plans a late follow-up for tomorrow and refuses a later day or an empty follow-up', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const longAgo = new Date();
    longAgo.setDate(longAgo.getDate() - 20);
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);

    component.form.patchValue({ appliedOn: longAgo, followUpInDays: 3 });
    expect(component.followUpDay).toContain(String(tomorrow.getDate()));

    component.form.patchValue({ appliedOn: tomorrow });
    fixture.detectChanges();
    element(fixture, '[data-confirm-apply]').click();
    fixture.detectChanges();
    expect(dialogRef.close).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).innerText).toContain("Une candidature ne peut pas être datée après aujourd'hui.");

    component.form.patchValue({ appliedOn: new Date(), followUpInDays: 0 });
    fixture.detectChanges();
    expect(component.followUpDay).toBeNull();
    element(fixture, '[data-confirm-apply]').click();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
