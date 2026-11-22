import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { ContactDto, ProjectDto } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { ContactService } from '../../../services/contact/contact.service';
import { EmailTemplateService } from '../../../services/email-template/email-template.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { UserProfile } from '../../../models/user-management.models';
import { ProjectEmailDialogComponent } from './project-email-dialog.component';
import fr from '../../../../assets/i18n/fr.json';

describe('ProjectEmailDialogComponent', () => {
  let contacts: jasmine.SpyObj<ContactService>;
  let templates: jasmine.SpyObj<EmailTemplateService>;
  let users: jasmine.SpyObj<UserManagementService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  const project: ProjectDto = { id: 8, role: 'Développeur Java', dailyRate: 650, clientId: 3, clientName: 'Airbus', middlemanId: 5, middlemanName: 'Alten' };
  const lea = { id: 11, firstName: 'Léa', lastName: 'Martin', email: 'lea@alten.fr', clientName: 'Alten' } as ContactDto;
  const paul = { id: 12, firstName: 'Paul', lastName: 'Durand', email: 'paul@airbus.fr', clientName: 'Airbus' } as ContactDto;
  const silent = { id: 13, firstName: 'Sans', lastName: 'Email', clientName: 'Airbus' } as ContactDto;
  let contactsWithBasis = false;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ProjectEmailDialogComponent>>;
  let ai: jasmine.SpyObj<AiService>;

  function create(setup: () => void = () => undefined, data: object = project): ComponentFixture<ProjectEmailDialogComponent> {
    registerLocaleData(localeFr);
    contacts = jasmine.createSpyObj('ContactService', ['getByClientId', 'emailStatus', 'sendEmail']);
    contacts.emailStatus.and.returnValue(of({ available: false, sentToday: 0, dailyMax: 50 }));
    contacts.getByClientId.and.callFake(id => of(id === 5 ? [contactsWithBasis ? { ...lea, legalBasis: 'CONSENT' } as ContactDto : lea] :
      [paul, silent]));
    templates = jasmine.createSpyObj('EmailTemplateService', ['list']);
    templates.list.and.returnValue(of([{ id: 4, name: 'Relance ESN', subject: 'Point {{role}}', body: 'Salut {{firstName}}, {{unknown}}' },
      { id: 5, name: 'Premier contact', subject: 'Bonjour', body: 'Bonjour {{firstName}}\n\n{{privacyNotice}}' }]));
    users = jasmine.createSpyObj('UserManagementService', ['getUserProfile']);
    users.getUserProfile.and.returnValue(of({ firstName: 'Ada', lastName: 'Lovelace', phone: '0600000000', availabilityDate: '2026-12-01' } as UserProfile));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    ai = jasmine.createSpyObj('AiService', ['polish']);
    setup();
    TestBed.configureTestingModule({
      imports: [ProjectEmailDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: ContactService, useValue: contacts },
        { provide: EmailTemplateService, useValue: templates },
        { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications },
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: AiService, useValue: ai }
      ]
    });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', fr);
    translate.use('fr');
    const fixture = TestBed.createComponent(ProjectEmailDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('writes the follow-up to the ESN\'s first contact, every variable filled', () => {
    const component = create().componentInstance;

    expect(component.contacts.map(contact => contact.id)).toEqual([11, 12]);
    expect(component.templates.map(template => template.name)).toEqual(['Relance', 'Proposition de TJM', 'Annonce de disponibilité', 'Révision du TJM au renouvellement', 'Relance ESN',
      'Premier contact']);
    expect(component.form.value.recipient).toBe(11);
    expect(component.form.value.subject).toBe('Suite à notre échange sur la mission Développeur Java');
    expect(component.form.value.body).toContain('Bonjour Léa,');
    expect(component.form.value.body).toContain('Ada Lovelace\n0600000000');
    expect(component.missing).toBeFalse();
    expect(component.mailto).toMatch(/^mailto:lea@alten\.fr\?subject=Suite%20%C3%A0/);
  });

  it('starts on the template it is opened for, a known one only', () => {
    const review = create(undefined, { ...project, emailTemplate: 'rateReview' }).componentInstance;
    expect(review.form.value.template).toBe('rateReview');
    expect(review.form.value.subject).toBe('Renouvellement de la mission Développeur Java : mon TJM');
    TestBed.resetTestingModule();
    expect(create(undefined, { ...project, emailTemplate: 'unknown' }).componentInstance.form.value.template).toBe('followUp');
  });

  it('writes again for another template or recipient, with the rate, company and availability', () => {
    const component = create().componentInstance;

    component.form.patchValue({ template: 'rateProposal', recipient: 12 });
    component.fill();
    expect(component.form.value.body).toContain('Bonjour Paul,');
    expect(component.form.value.body).toContain('chez Airbus, mon TJM est de 650 € HT');

    component.form.patchValue({ template: 'availability' });
    component.fill();
    expect(component.form.value.subject).toBe('Disponible à partir du 1 décembre 2026');

    component.form.patchValue({ template: 'own-4', recipient: null });
    component.fill();
    expect(component.form.value.subject).toBe('Point Développeur Java');
    expect(component.form.value.body).toBe('Salut {{firstName}}, {{unknown}}');
    expect(component.missing).toBeTrue();
    expect(component.mailto).toMatch(/^mailto:\?subject=/);
  });

  it('adds a privacy notice naming the sender and why the recipient\'s details are kept', () => {
    contactsWithBasis = true;
    const component = create().componentInstance;

    component.form.patchValue({ template: 'own-5', recipient: 11 });
    component.fill();
    expect(component.form.value.body).toBe('Bonjour Léa\n\nVos coordonnées professionnelles sont conservées par Ada Lovelace pour le suivi de ' +
      'nos échanges (consentement). Vous pouvez à tout moment demander à les consulter, les rectifier ou les effacer en répondant à cet e-mail.');
    component.form.patchValue({ recipient: 12 });
    component.fill();
    expect(component.form.value.body).toContain('(intérêt légitime)');
    contactsWithBasis = false;
  });

  it('copies the subject and the message, and says when the browser refuses', async () => {
    const component = create().componentInstance;
    const write = spyOn(navigator.clipboard, 'writeText').and.returnValues(Promise.resolve(), Promise.reject(new Error('denied')));

    component.copy();
    await Promise.resolve();
    component.copy();
    await new Promise(resolve => setTimeout(resolve));

    expect(write.calls.first().args[0]).toMatch(/^Suite à notre échange sur la mission Développeur Java\n\nBonjour Léa,/);
    expect(notifications.success).toHaveBeenCalledWith('emails.copied');
    expect(notifications.error).toHaveBeenCalledWith('emails.copyError');
  });

  it('still writes from the built-in templates when contacts, templates or the profile cannot be loaded', () => {
    const component = create(() => {
      contacts.getByClientId.and.returnValue(throwError(() => new Error('down')));
      templates.list.and.returnValue(throwError(() => new Error('down')));
      users.getUserProfile.and.returnValue(throwError(() => new Error('down')));
    }).componentInstance;

    expect(component.contacts).toEqual([]);
    expect(component.templates.length).toBe(4);
    expect(component.form.value.body).toContain('Bonjour {{firstName}},');
    expect(notifications.error).toHaveBeenCalledOnceWith('emails.loadError');
  });

  it('asks for no contacts for an opportunity without client', () => {
    TestBed.overrideProvider(MAT_DIALOG_DATA, { useValue: { ...project, clientId: undefined, middlemanId: undefined } });
    const component = create().componentInstance;

    expect(contacts.getByClientId).not.toHaveBeenCalled();
    expect(component.contacts).toEqual([]);
  });

  it('offers to send from Indezy only once email is set up', () => {
    const fixture = create();
    expect(fixture.nativeElement.querySelector('[data-email-send]')).toBeNull();
    expect(fixture.componentInstance.canSend).toBeFalse();

    TestBed.resetTestingModule();
    const ready = create(() => contacts.emailStatus.and.returnValue(of({ available: true, sentToday: 3, dailyMax: 50 })));
    expect(ready.nativeElement.querySelector('[data-email-send]')).not.toBeNull();
    expect(ready.nativeElement.querySelector('[data-email-track]')).not.toBeNull();
  });

  it('sends the email from Indezy, tracked when asked, and closes', () => {
    const component = create(() => contacts.emailStatus.and.returnValue(of({ available: true, sentToday: 0, dailyMax: 50 }))).componentInstance;
    contacts.sendEmail.and.returnValue(of({ id: 1, occurredOn: '2026-11-22', channel: 'EMAIL', direction: 'OUTGOING' }));
    component.form.patchValue({ template: 'own-5', track: true });
    component.fill();
    expect(component.canSend).toBeTrue();
    component.send();

    expect(contacts.sendEmail).toHaveBeenCalledOnceWith(11, jasmine.objectContaining({ projectId: 8, subject: 'Bonjour', track: true,
      copy: true, language: 'FR' }));
    expect(notifications.success).toHaveBeenCalledWith('emails.send.sent', undefined, { name: 'Léa Martin' });
    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('sends nothing with a variable left, and says why a send failed', () => {
    const component = create(() => contacts.emailStatus.and.returnValue(of({ available: true, sentToday: 50, dailyMax: 50 }))).componentInstance;
    component.form.patchValue({ subject: 'Point', body: 'Salut {{firstName}}' });
    expect(component.missing).toBeTrue();
    expect(component.canSend).toBeFalse();
    component.send();
    expect(contacts.sendEmail).not.toHaveBeenCalled();

    component.form.patchValue({ subject: 'Point', body: 'Salut' });
    contacts.sendEmail.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Up to 50 emails a day go' } })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('emails.send.dailyLimit');
    contacts.sendEmail.and.returnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('emails.send.error');
    expect(component.sendingNow).toBeFalse();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('still opens the mail app when the email status cannot be read', () => {
    const fixture = create(() => contacts.emailStatus.and.returnValue(throwError(() => new Error('down'))));
    expect(fixture.componentInstance.sending).toBeNull();
    expect(fixture.nativeElement.querySelector('a[href^="mailto:"]')).not.toBeNull();
  });

  it('polishes the message in the tone chosen, and undoes it', () => {
    const component = create().componentInstance;
    component.form.patchValue({ subject: 'point', body: 'bonjour {{firstName}}' });
    ai.polish.and.returnValue(of({ subject: 'Point', body: 'Bonjour {{firstName}},' }));
    component.polish('FORMAL');
    expect(ai.polish).toHaveBeenCalledOnceWith('point', 'bonjour {{firstName}}', 'FORMAL');
    expect(component.form.value.body).toBe('Bonjour {{firstName}},');
    expect(component.form.value.subject).toBe('Point');
    component.undoPolish();
    expect(component.form.value.body).toBe('bonjour {{firstName}}');
    expect(component.beforePolish).toBeNull();
  });

  it('polishes nothing empty, and says why a polish failed', () => {
    const component = create().componentInstance;
    component.form.patchValue({ body: '  ' });
    component.polish('KEEP');
    expect(ai.polish).not.toHaveBeenCalled();
    component.form.patchValue({ body: 'bonjour' });
    ai.polish.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409,
      error: { message: "The AI changed the message's variables: polish it again or edit it by hand" } })));
    component.polish('SHORTER');
    expect(notifications.error).toHaveBeenCalledWith('ai.errors.variables');
    expect(component.polishing).toBeFalse();
    expect(component.form.value.body).toBe('bonjour');
  });
});
