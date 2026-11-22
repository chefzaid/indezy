import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AvailabilityRecipient, ContactInteraction } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { AvailabilityEmailsComponent } from './availability-emails.component';

describe('AvailabilityEmailsComponent', () => {
  const lea: AvailabilityRecipient = { id: 1, firstName: 'Léa', name: 'Léa Martin', company: 'Conseil', email: 'lea@conseil.example', answers: 3,
    lastContactedOn: '2026-08-01' };
  const marc: AvailabilityRecipient = { id: 2, firstName: null, name: 'Marc Durand', company: 'Autre', email: 'marc@autre.example', answers: 0,
    lastContactedOn: null };

  function create(recipients = of([lea, marc])) {
    const contacts = jasmine.createSpyObj('ContactService', ['availabilityRecipients', 'sendEmail']);
    contacts.availabilityRecipients.and.returnValue(recipients);
    const notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    TestBed.configureTestingModule({
      imports: [AvailabilityEmailsComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: ContactService, useValue: contacts },
        { provide: NotificationService, useValue: notifications }]
    });
    const fixture = TestBed.createComponent(AvailabilityEmailsComponent);
    fixture.componentRef.setInput('availableFrom', '2027-02-01');
    fixture.componentRef.setInput('text', 'Dispo en février pour du Java.');
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, contacts, notifications };
  }

  it('writes the email as it opens and sends one to each contact ticked, with the dossier', () => {
    const { fixture, component, contacts, notifications } = create();
    const details = (fixture.nativeElement as HTMLElement).querySelector('details')!;
    details.open = true;
    component.opened({ target: details } as unknown as Event);
    expect(component.subject).toBe('Disponible à partir du 1 février 2027');
    expect(component.body).toContain('Bonjour {{firstName}},\n\nDispo en février pour du Java.');
    expect(component.body).toContain('availabilityEmails.attached');
    // Opened again, what was written stays.
    component.body = 'Mon texte {{firstName}} chez {{company}}';
    component.opened({ target: details } as unknown as Event);
    expect(component.body).toBe('Mon texte {{firstName}} chez {{company}}');
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-availability-recipient="1"]')!.textContent)
      .toContain('availabilityEmails.answers');

    component.send();
    expect(contacts.sendEmail).not.toHaveBeenCalled();
    component.toggle(1);
    component.toggle(2);
    contacts.sendEmail.and.callFake((id: number) => id === 2 ? throwError(() => new Error('down')) : of({} as ContactInteraction));
    component.send();
    expect(contacts.sendEmail.calls.allArgs()).toEqual([
      [1, { subject: 'Disponible à partir du 1 février 2027', body: 'Mon texte Léa chez Conseil', track: false, copy: false, language: 'FR', dossier: true }],
      [2, jasmine.objectContaining({ body: 'Mon texte Marc Durand chez Autre' })]]);
    expect(notifications.success).toHaveBeenCalledWith('availabilityEmails.sent', 4000, { count: 1 });
    expect(notifications.error).toHaveBeenCalledWith('availabilityEmails.failed', 8000, { names: 'Marc Durand' });
    expect([...component.chosen]).toEqual([2]);
    component.toggle(2);
    expect(component.chosen.size).toBe(0);
  });

  it('says when no ESN contact can be written to, or the list cannot be read', () => {
    const { fixture } = create(of([]));
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-availability-emails-none]')).not.toBeNull();
    TestBed.resetTestingModule();
    const failing = create(throwError(() => new Error('down')));
    expect(failing.component.recipients).toEqual([]);
    failing.fixture.componentRef.setInput('availableFrom', null);
    failing.fixture.componentRef.setInput('language', 'EN');
    const details = document.createElement('details');
    details.open = true;
    failing.component.opened({ target: details } as unknown as Event);
    expect(failing.component.subject).toBe('My availability');
    expect(failing.component.body).toContain('Hello {{firstName}}');
  });
});
