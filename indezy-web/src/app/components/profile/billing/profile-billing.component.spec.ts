import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { BillingIdentity } from '../../../models';
import { BillingService } from '../../../services/billing/billing.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProfileBillingComponent } from './profile-billing.component';
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
      providers: [provideNoopAnimations(), { provide: BillingService, useValue: billing }, { provide: NotificationService, useValue: notifications }]
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
    billing = jasmine.createSpyObj('BillingService', ['identity', 'saveIdentity']);
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
      bic: 'bnpafrpp', shareCapital: 1000, registration: 'RCS Paris' });

    component.save();

    expect(billing.saveIdentity).toHaveBeenCalledWith(jasmine.objectContaining({ siret: '55210055400012', iban: 'FR7630006000011234567890189',
      bic: 'BNPAFRPP', shareCapital: null, registration: null, vatExempt: true }));
    expect(component.complete).toBeTrue();
    expect(notifications.success).toHaveBeenCalledWith('profile.billing.saved');

    component.form.patchValue({ legalForm: 'SAS', shareCapital: 1000, registration: ' RCS Paris 552 100 554 ', vatNumber: '', bic: '',
      legalName: ' ', address: '', invoicePrefix: '' });
    component.save();
    expect(billing.saveIdentity).toHaveBeenCalledWith(jasmine.objectContaining({ shareCapital: 1000, registration: 'RCS Paris 552 100 554',
      vatNumber: null, bic: null, legalName: null, address: null, invoicePrefix: null }));
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
});
