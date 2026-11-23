import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { BillingIdentity } from '../../../models';
import { BillingService } from '../../../services/billing/billing.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProfileBillingComponent, contrastOnWhite } from './profile-billing.component';
import en from '../../../../assets/i18n/en.json';

describe('ProfileBillingComponent', () => {
  let billing: jasmine.SpyObj<BillingService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const defaults: BillingIdentity = { legalName: 'Alice Martin', legalForm: null, siret: null, vatNumber: null, vatExempt: true,
    vatRate: 20, shareCapital: null, registration: null, address: '18 rue du Sentier\nParis', iban: null, bic: null, paymentTermDays: 30,
    invoicePrefix: 'F', complete: false };

  function create(read = of(defaults)): ComponentFixture<ProfileBillingComponent> {
    billing.identity.and.returnValue(read);
    TestBed.configureTestingModule({
      imports: [ProfileBillingComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: BillingService, useValue: billing }, { provide: NotificationService, useValue: notifications }]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('en', en);
    translate.use('en');
    const fixture = TestBed.createComponent(ProfileBillingComponent);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<ProfileBillingComponent>): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  beforeEach(() => {
    billing = jasmine.createSpyObj('BillingService', ['identity', 'saveIdentity', 'uploadLogo', 'removeLogo', 'logo', 'uploadTermsFile', 'removeTermsFile', 'termsFile',
      'uploadFont', 'removeFont', 'uploadLetterhead', 'removeLetterhead', 'letterhead', 'stylePreview']);
    billing.logo.and.returnValue(of(new Blob(['png'], { type: 'image/png' })));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
  });

  it('shows the identity and what it lacks to issue invoices', () => {
    const fixture = create();
    expect(fixture.componentInstance.form.value.legalName).toBe('Alice Martin');
    expect(text(fixture)).toContain('Fill in the SIRET');
    expect((fixture.nativeElement as HTMLElement).querySelector('input[formControlName="vatNumber"]')).toBeNull();
  });

  it('asks a company for its capital and registration, a VAT payer for its number, and tells portage apart', () => {
    const fixture = create();
    const form = fixture.componentInstance.form;
    form.patchValue({ legalForm: 'SASU', vatExempt: false });
    expect(text(fixture)).toContain('Share capital');
    expect(text(fixture)).toContain('VAT number');
    form.patchValue({ legalForm: 'PORTAGE' });
    expect(text(fixture)).toContain('the portage company invoices your clients');
  });

  it('saves the identity compacted, keeping a company\'s fields only for a company', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    billing.saveIdentity.and.returnValue(of({ ...defaults, siret: '55210055400012', complete: true }));
    component.form.patchValue({ legalForm: 'MICRO_ENTREPRISE', siret: '552 100 554 00012', iban: 'fr76 3000 6000 0112 3456 7890 189',
      bic: 'bnpafrpp', shareCapital: 1000, registration: 'RCS Paris', latePaymentRate: 12.15, quoteTerms: '  Article 1  ', footer: ' alice.dev ', quoteNotes: ' Sur devis. ', header: ' Architecte Java ' });

    component.save();

    expect(billing.saveIdentity).toHaveBeenCalledWith(jasmine.objectContaining({ siret: '55210055400012', iban: 'FR7630006000011234567890189',
      bic: 'BNPAFRPP', shareCapital: null, registration: null, vatExempt: true, latePaymentRate: 12.15, quoteTerms: 'Article 1', footer: 'alice.dev', quoteNotes: 'Sur devis.', invoiceNotes: null, logoPlacement: 'LEFT', logoSize: 'MEDIUM', header: 'Architecte Java', font: 'SANS' }));
    expect(component.complete).toBeTrue();
    expect(notifications.success).toHaveBeenCalledWith('profile.billing.saved');

    component.form.patchValue({ legalForm: 'SAS', shareCapital: 1000, registration: ' RCS Paris 552 100 554 ', vatNumber: '', bic: '',
      legalName: ' ', address: '', invoicePrefix: '' });
    component.save();
    expect(billing.saveIdentity).toHaveBeenCalledWith(jasmine.objectContaining({ shareCapital: 1000, registration: 'RCS Paris 552 100 554',
      vatNumber: null, bic: null, legalName: null, address: null, invoicePrefix: null }));
    // A rate beyond 100 % is refused before it is sent.
    component.form.patchValue({ latePaymentRate: 150 });
    expect(component.form.controls.latePaymentRate.invalid).toBeTrue();
  });

  it('refuses a malformed IBAN, and says when it cannot save or read', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.form.patchValue({ iban: 'FR7630006000011234567890188' });
    component.save();
    expect(billing.saveIdentity).not.toHaveBeenCalled();
    expect(component.error('iban')).toBe('This IBAN is not valid');

    component.form.patchValue({ iban: '' });
    billing.saveIdentity.and.returnValue(throwError(() => new Error('500')));
    component.save();
    expect(component.saving).toBeFalse();
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.saveError');

    TestBed.resetTestingModule();
    create(throwError(() => new Error('500')));
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.loadError');
  });

  it('takes an accent colour dark enough to read, refusing a pale one', () => {
    const component = create().componentInstance;
    component.setAccent({ target: { value: '#ffee88' } } as unknown as Event);
    expect(component.accentTooPale).toBeTrue();
    component.save();
    expect(billing.saveIdentity).not.toHaveBeenCalled();
    component.setAccent({ target: { value: '#1f4e79' } } as unknown as Event);
    expect(component.form.value.accentColor).toBe('#1F4E79');
    expect(component.accentTooPale).toBeFalse();
    component.clearAccent();
    expect(component.form.value.accentColor).toBe('');
    expect(contrastOnWhite('#000000')).toBeCloseTo(21, 0);
  });

  it('shows, replaces and removes the documents\' logo', () => {
    const fixture = create(of({ ...defaults, logoId: 3 }));
    expect(billing.logo).toHaveBeenCalledWith(3);
    const component = fixture.componentInstance;
    billing.logo.and.returnValue(of(new Blob(['png'], { type: 'image/png' })));
    billing.uploadLogo.and.returnValue(of({ ...defaults, logoId: 4 }));
    const input = document.createElement('input');
    input.type = 'file';
    const files = new DataTransfer();
    files.items.add(new File(['png'], 'logo.png', { type: 'image/png' }));
    input.files = files.files;
    component.uploadLogo({ target: input } as unknown as Event);
    expect(billing.uploadLogo).toHaveBeenCalled();
    expect(component.logoId).toBe(4);
    expect(component.logoUrl).toContain('blob:');
    expect(notifications.success).toHaveBeenCalledWith('profile.billing.logoSaved');

    const gif = new DataTransfer();
    gif.items.add(new File(['gif'], 'logo.gif', { type: 'image/gif' }));
    input.files = gif.files;
    component.uploadLogo({ target: input } as unknown as Event);
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.logoRefused');
    expect(billing.uploadLogo).toHaveBeenCalledTimes(1);

    billing.removeLogo.and.returnValue(of({ ...defaults, logoId: null }));
    component.removeLogo();
    expect(component.logoId).toBeNull();
    expect(component.logoUrl).toBeNull();
    fixture.destroy();
  });

  it('appends a terms and conditions PDF to the quotes, opens and removes it', () => {
    const component = create().componentInstance;
    billing.uploadTermsFile.and.returnValue(of({ ...defaults, quoteTermsFileId: 7 }));
    const input = document.createElement('input');
    input.type = 'file';
    const files = new DataTransfer();
    files.items.add(new File(['%PDF'], 'cgv.pdf', { type: 'application/pdf' }));
    input.files = files.files;
    component.uploadTermsFile({ target: input } as unknown as Event);
    expect(component.termsFileId).toBe(7);
    expect(notifications.success).toHaveBeenCalledWith('profile.billing.termsFileSaved');
    const click = spyOn(HTMLAnchorElement.prototype, 'click');
    billing.termsFile.and.returnValue(of(new Blob(['%PDF'])));
    component.openTermsFile();
    expect(billing.termsFile).toHaveBeenCalledWith(7);
    expect(click).toHaveBeenCalled();
    const png = new DataTransfer();
    png.items.add(new File(['png'], 'cgv.png', { type: 'image/png' }));
    input.files = png.files;
    component.uploadTermsFile({ target: input } as unknown as Event);
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.termsFileRefused');
    billing.removeTermsFile.and.returnValue(of({ ...defaults, quoteTermsFileId: null }));
    component.removeTermsFile();
    expect(component.termsFileId).toBeNull();
  });

  it('uploads a font with its bold face, says why one is refused, and goes back to a bundled one', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const input = document.createElement('input');
    input.type = 'file';
    const pick = (...names: string[]): void => {
      const files = new DataTransfer();
      names.forEach(name => files.items.add(new File(['ttf'], name)));
      input.files = files.files;
      component.uploadFont({ target: input } as unknown as Event);
    };
    billing.uploadFont.and.returnValue(of({ ...defaults, font: 'CUSTOM', fontFileId: 7, fontFamily: 'Ma Police' }));
    pick('MaPolice-Bold.ttf', 'MaPolice-Regular.ttf');
    const [regular, bold] = billing.uploadFont.calls.mostRecent().args;
    expect(regular.name).toBe('MaPolice-Regular.ttf');
    expect(bold?.name).toBe('MaPolice-Bold.ttf');
    expect(component.fontFamily).toBe('Ma Police');
    expect(component.form.value.font).toBe('CUSTOM');
    expect(notifications.success).toHaveBeenCalledWith('profile.billing.fontSaved', undefined, { family: 'Ma Police' });
    expect(text(fixture)).toContain('Replace my font');

    pick('police.woff');
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.fontRefused');
    const refusal = (message: string): void => {
      billing.uploadFont.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message } })));
      pick('Police.ttf');
    };
    refusal('This font\'s licence does not allow embedding it in a document');
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.fontLicence');
    refusal('This OpenType font draws its letters with CFF outlines');
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.fontCff');
    refusal('This font lacks letters an invoice prints: €');
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.fontLetters');
    expect(billing.uploadFont.calls.mostRecent().args[1]).toBeNull();

    billing.removeFont.and.returnValue(of({ ...defaults, font: 'SANS' }));
    component.removeFont();
    expect(component.fontFamily).toBeNull();
    expect(component.form.value.font).toBe('SANS');
    billing.removeFont.and.returnValue(throwError(() => new Error('down')));
    component.removeFont();
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.saveError');
  });

  it('draws the documents over a letterhead, says why one is refused, opens and removes it', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    const input = document.createElement('input');
    input.type = 'file';
    const pick = (name: string, type: string): void => {
      const files = new DataTransfer();
      files.items.add(new File(['%PDF'], name, { type }));
      input.files = files.files;
      component.uploadLetterhead({ target: input } as unknown as Event);
    };
    billing.uploadLetterhead.and.returnValue(of({ ...defaults, letterheadId: 6 }));
    pick('papier.pdf', 'application/pdf');
    expect(component.letterheadId).toBe(6);
    expect(notifications.success).toHaveBeenCalledWith('profile.billing.letterheadSaved');
    expect(text(fixture)).toContain('Replace the letterhead');

    pick('papier.png', 'image/png');
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.letterheadRefused');
    for (const [message, key] of [['A letterhead is an upright A4 page', 'letterheadSize'], ['uses fonts it does not embed', 'letterheadFonts'],
      ['A letterhead has one or two pages', 'letterheadPages'], ['other', 'letterheadRefused']]) {
      billing.uploadLetterhead.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message } })));
      pick('papier.pdf', 'application/pdf');
      expect(notifications.error).toHaveBeenCalledWith(`profile.billing.${key}`);
    }

    const link = jasmine.createSpyObj('a', ['click']);
    spyOn(document, 'createElement').and.callFake(() => link as never);
    billing.letterhead.and.returnValue(of(new Blob(['%PDF'])));
    component.openLetterhead();
    expect(billing.letterhead).toHaveBeenCalledWith(6);
    billing.removeLetterhead.and.returnValue(of({ ...defaults, letterheadId: null }));
    component.removeLetterhead();
    expect(component.letterheadId).toBeNull();
    component.openLetterhead();
    expect(billing.letterhead).toHaveBeenCalledTimes(1);
  });

  it('previews the style the form sets, saying when it cannot', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    component.form.patchValue({ theme: 'MODERN', accentColor: '#1F4E79' });
    billing.stylePreview.and.returnValue(of(new Blob(['png'], { type: 'image/png' })));
    component.preview();
    expect(billing.stylePreview).toHaveBeenCalledWith(jasmine.objectContaining({ theme: 'MODERN', accentColor: '#1F4E79' }));
    expect(component.previewUrl).toContain('blob:');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-billing-preview] img')).not.toBeNull();
    billing.stylePreview.and.returnValue(throwError(() => new Error('down')));
    component.preview();
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.previewError');
    component.form.patchValue({ iban: 'FR76 1234' });
    component.preview();
    expect(billing.stylePreview).toHaveBeenCalledTimes(2);
    fixture.destroy();
  });
});
