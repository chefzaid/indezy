import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { MAT_DIALOG_DATA } from '@angular/material/dialog';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { ContactDto, ProjectDto } from '../../../models';
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

  function create(setup: () => void = () => undefined): ComponentFixture<ProjectEmailDialogComponent> {
    registerLocaleData(localeFr);
    contacts = jasmine.createSpyObj('ContactService', ['getByClientId']);
    contacts.getByClientId.and.callFake(id => of(id === 5 ? [lea] : [paul, silent]));
    templates = jasmine.createSpyObj('EmailTemplateService', ['list']);
    templates.list.and.returnValue(of([{ id: 4, name: 'Relance ESN', subject: 'Point {{role}}', body: 'Salut {{firstName}}, {{unknown}}' }]));
    users = jasmine.createSpyObj('UserManagementService', ['getUserProfile']);
    users.getUserProfile.and.returnValue(of({ firstName: 'Ada', lastName: 'Lovelace', phone: '0600000000', availabilityDate: '2026-12-01' } as UserProfile));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    setup();
    TestBed.configureTestingModule({
      imports: [ProjectEmailDialogComponent, TranslateModule.forRoot()],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: project },
        { provide: LOCALE_ID, useValue: 'fr-FR' },
        { provide: ContactService, useValue: contacts },
        { provide: EmailTemplateService, useValue: templates },
        { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications }
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
    expect(component.templates.map(template => template.name)).toEqual(['Relance', 'Proposition de TJM', 'Annonce de disponibilité', 'Relance ESN']);
    expect(component.form.value.recipient).toBe(11);
    expect(component.form.value.subject).toBe('Suite à notre échange sur la mission Développeur Java');
    expect(component.form.value.body).toContain('Bonjour Léa,');
    expect(component.form.value.body).toContain('Ada Lovelace\n0600000000');
    expect(component.missing).toBeFalse();
    expect(component.mailto).toMatch(/^mailto:lea@alten\.fr\?subject=Suite%20%C3%A0/);
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
    expect(component.templates.length).toBe(3);
    expect(component.form.value.body).toContain('Bonjour {{firstName}},');
    expect(notifications.error).toHaveBeenCalledOnceWith('emails.loadError');
  });

  it('asks for no contacts for an opportunity without client', () => {
    TestBed.overrideProvider(MAT_DIALOG_DATA, { useValue: { ...project, clientId: undefined, middlemanId: undefined } });
    const component = create().componentInstance;

    expect(contacts.getByClientId).not.toHaveBeenCalled();
    expect(component.contacts).toEqual([]);
  });
});
