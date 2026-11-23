import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { DocumentTemplate } from '../../../models';
import { BillingService } from '../../../services/billing/billing.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { BillingTemplatesComponent } from './billing-templates.component';

describe('BillingTemplatesComponent', () => {
  let billing: jasmine.SpyObj<BillingService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const invoiceTemplate: DocumentTemplate = { kind: 'INVOICE', fileName: 'modele.docx', uploadedAt: '2026-11-23T10:00:00',
    found: ['invoice.number', '#lines'], unknown: ['iban.inconnu'], missing: ['mentions'] };

  function create(): { component: BillingTemplatesComponent; element: () => HTMLElement } {
    billing = jasmine.createSpyObj('BillingService', ['templates', 'placeholders', 'uploadTemplate', 'removeTemplate', 'templatePreview']);
    billing.templates.and.returnValue(of([invoiceTemplate]));
    billing.placeholders.and.returnValue(of(['document.number', '#lines', '/lines', 'logo']));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [BillingTemplatesComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), { provide: BillingService, useValue: billing }, { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(BillingTemplatesComponent);
    fixture.detectChanges();
    return { component: fixture.componentInstance, element: () => {
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    } };
  }

  function picked(name: string): Event {
    const input = document.createElement('input');
    input.type = 'file';
    const files = new DataTransfer();
    files.items.add(new File(['PK'], name));
    input.files = files.files;
    return { target: input } as unknown as Event;
  }

  it('shows each template with what it lacks, and the placeholders to copy', () => {
    const { component, element } = create();
    expect(element().querySelector('[data-billing-template="INVOICE"]')?.textContent).toContain('modele.docx');
    expect(element().querySelector('[data-billing-template-unknown]')).not.toBeNull();
    expect(element().querySelector('[data-billing-template-missing]')).not.toBeNull();
    expect(component.template('QUOTE')).toBeUndefined();
    expect(element().querySelectorAll('[data-billing-placeholders] li').length).toBe(4);
    expect(element().querySelector('[data-billing-placeholders] code')?.textContent).toBe('{{document.number}}');
    expect(component.key('#lines')).toBe('lines');
    expect(component.key('/lines')).toBe('linesEnd');
    expect(component.key('seller.name')).toBe('seller_name');
    expect(component.key('^vatExempt')).toBe('not_vatExempt');
    expect(component.key('/vatExempt')).toBe('vatExemptEnd');
    expect(component.missingNames(['mentions', 'lines'])).toBe('mentions, lines');
  });

  it('uploads a template, saying why one is refused, and removes it', () => {
    const { component } = create();
    billing.uploadTemplate.and.returnValue(of({ ...invoiceTemplate, kind: 'QUOTE', fileName: 'devis.docx' }));
    component.upload('QUOTE', picked('devis.docx'));
    expect(billing.uploadTemplate).toHaveBeenCalledWith('QUOTE', jasmine.any(File));
    expect(component.template('QUOTE')?.fileName).toBe('devis.docx');
    expect(notifications.success).toHaveBeenCalledWith('billingTemplates.saved');

    component.upload('QUOTE', picked('devis.odt'));
    expect(notifications.error).toHaveBeenCalledWith('billingTemplates.refused');
    // A fillable PDF form goes up too, refused without fields or with scripts.
    billing.uploadTemplate.and.returnValue(of({ ...invoiceTemplate, kind: 'QUOTE', fileName: 'formulaire.pdf' }));
    component.upload('QUOTE', picked('formulaire.pdf'));
    expect(component.template('QUOTE')?.fileName).toBe('formulaire.pdf');
    billing.uploadTemplate.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'This PDF has no form field' } })));
    component.upload('QUOTE', picked('plat.pdf'));
    expect(notifications.error).toHaveBeenCalledWith('billingTemplates.noField');
    billing.uploadTemplate.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'This PDF holds scripts' } })));
    component.upload('QUOTE', picked('script.pdf'));
    expect(notifications.error).toHaveBeenCalledWith('billingTemplates.pdfRefused');
    billing.uploadTemplate.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'holds no placeholder' } })));
    component.upload('INVOICE', picked('vide.docx'));
    expect(notifications.error).toHaveBeenCalledWith('billingTemplates.noPlaceholder', undefined, { example: '{{invoice.number}}' });
    billing.uploadTemplate.and.returnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { message: 'holds macros' } })));
    component.upload('INVOICE', picked('macro.docx'));
    expect(notifications.error).toHaveBeenCalledWith('billingTemplates.macros');
    component.upload('INVOICE', { target: document.createElement('input') } as unknown as Event);
    expect(billing.uploadTemplate).toHaveBeenCalledTimes(6);

    billing.removeTemplate.and.returnValue(of(undefined));
    component.remove('INVOICE');
    expect(component.template('INVOICE')).toBeUndefined();
    billing.removeTemplate.and.returnValue(throwError(() => new Error('down')));
    component.remove('QUOTE');
    expect(notifications.error).toHaveBeenCalledWith('profile.billing.saveError');
  });

  it('copies a placeholder with its braces', async () => {
    const { component } = create();
    const write = spyOn(navigator.clipboard, 'writeText').and.returnValue(Promise.resolve());
    component.copy('client.name');
    await Promise.resolve();
    expect(write).toHaveBeenCalledWith('{{client.name}}');
  });

  it('downloads the template filled, saying when it cannot', () => {
    const { component } = create();
    const link = jasmine.createSpyObj('a', ['click']);
    spyOn(document, 'createElement').and.callFake(() => link as never);
    billing.templatePreview.and.returnValue(of(new Blob(['PK'])));
    component.preview('QUOTE');
    expect(billing.templatePreview).toHaveBeenCalledWith('QUOTE');
    expect(link.download).toBe('apercu-modele-devis.docx');
    billing.templatePreview.and.returnValue(of(new Blob(['%PDF'], { type: 'application/pdf' })));
    component.preview('INVOICE');
    expect(link.download).toBe('apercu-modele-facture.pdf');
    billing.templatePreview.and.returnValue(throwError(() => new Error('down')));
    component.preview('INVOICE');
    expect(notifications.error).toHaveBeenCalledWith('billingTemplates.previewError');
  });
});
