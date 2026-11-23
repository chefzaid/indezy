import { ChangeDetectorRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { AvailabilityRecipient, ContactInteraction } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { AvailabilityEmailsComponent } from './availability-emails.component';

describe('AvailabilityEmailsComponent', () => {
  const lea: AvailabilityRecipient = { id: 1, firstName: 'Léa', name: 'Léa Martin', company: 'Conseil', email: 'lea@conseil.example', answers: 3,
    lastContactedOn: '2026-08-01', postings: 0, mission: null, silentCampaigns: 0, campaignOpportunities: 1, ratePaid: null };
  const marc: AvailabilityRecipient = { id: 2, firstName: null, name: 'Marc Durand', company: 'Autre', email: 'marc@autre.example', answers: 0,
    lastContactedOn: null, postings: 2, mission: 'Lead Java', silentCampaigns: 0, campaignOpportunities: 0 };

  function create(recipients = of([lea, marc])) {
    const contacts = jasmine.createSpyObj('ContactService', ['availabilityRecipients', 'sendEmail', 'scheduledEmails', 'scheduleEmails',
      'cancelScheduledEmail', 'startCampaign', 'campaigns']);
    contacts.startCampaign.and.returnValue(of({ id: 3, name: 'x', startedAt: '2026-11-23T08:00:00', sent: 0, waiting: 0, answered: 0,
      opportunities: 0, won: 0, contactIds: [] }));
    contacts.campaigns.and.returnValue(of([]));
    contacts.availabilityRecipients.and.returnValue(recipients);
    contacts.scheduledEmails.and.returnValue(of([]));
    const notifications = jasmine.createSpyObj('NotificationService', ['success', 'error', 'info']);
    const ai = jasmine.createSpyObj('AiService', ['prospectionMessages']);
    TestBed.configureTestingModule({
      imports: [AvailabilityEmailsComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: ContactService, useValue: contacts },
        { provide: NotificationService, useValue: notifications }, { provide: AiService, useValue: ai }]
    });
    const fixture = TestBed.createComponent(AvailabilityEmailsComponent);
    fixture.componentRef.setInput('availableFrom', '2027-02-01');
    fixture.componentRef.setInput('text', 'Dispo en février pour du Java.');
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, contacts, notifications, ai };
  }

  it('lists the likeliest 20 contacts first, the others on asking, with the rate each company paid', () => {
    const many = Array.from({ length: 23 }, (_, index) => ({ ...marc, id: 100 + index, name: 'Contact ' + index, ratePaid: index ? null : 650 }));
    const { fixture, component } = create(of(many));
    const element = fixture.nativeElement as HTMLElement;
    const details = element.querySelector('details')!;
    details.open = true;
    component.opened({ target: details } as unknown as Event);
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-availability-recipient]').length).toBe(20);
    expect(element.querySelector('[data-availability-recipient="100"] [data-recipient-rate-paid]')!.textContent).toContain('availabilityEmails.ratePaid');
    (element.querySelector('[data-availability-emails-show-all]') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.querySelectorAll('[data-availability-recipient]').length).toBe(23);
    expect(element.querySelector('[data-availability-emails-show-all]')).toBeNull();
  });

  it('writes each ticked contact\'s own message with AI, kept to read with Edit', () => {
    const { fixture, component, notifications, ai } = create();
    component.subject = 'Disponible';
    component.body = 'Bonjour {{firstName}},';
    component.toggle(1);
    component.toggle(2);
    ai.prospectionMessages.and.returnValue(of([{ contactId: 1, body: 'Bonjour Léa, ravi de vous recroiser.' }]));
    component.writeEach();
    expect(ai.prospectionMessages).toHaveBeenCalledWith('Disponible', 'Bonjour {{firstName}},', [1, 2], 'FR');
    expect(component.own.get(1)).toBe('Bonjour Léa, ravi de vous recroiser.');
    expect(component.own.has(2)).toBeFalse();
    expect(notifications.success).toHaveBeenCalledWith('availabilityEmails.writtenEach', 6000, { count: 1 });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-recipient-edit="1"]')!.textContent).toContain('availabilityEmails.edited');

    ai.prospectionMessages.and.returnValue(throwError(() => new Error('503')));
    component.writeEach();
    expect(notifications.error).toHaveBeenCalledWith('availabilityEmails.writeEachError');
    expect(component.writing).toBeFalse();
  });

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
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-availability-recipient="2"] [data-recipient-postings]')!.textContent)
      .toContain('availabilityEmails.postings');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-availability-recipient="1"] [data-recipient-postings]')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-availability-recipient="1"] [data-recipient-campaign-opportunities]')!
      .textContent).toContain('availabilityEmails.campaignOpportunities');
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-recipient-silent]')).toBeNull();

    component.send();
    expect(contacts.sendEmail).not.toHaveBeenCalled();
    component.toggle(1);
    component.toggle(2);
    contacts.sendEmail.and.callFake((id: number) => id === 2 ? throwError(() => new Error('down')) : of({} as ContactInteraction));
    component.send();
    expect(contacts.sendEmail.calls.allArgs()).toEqual([
      [1, { subject: 'Disponible à partir du 1 février 2027', body: 'Mon texte Léa chez Conseil', track: false, copy: false, language: 'FR', dossier: true,
        campaignId: 3 }],
      [2, jasmine.objectContaining({ body: 'Mon texte Marc Durand chez Autre' })]]);
    expect(notifications.success).toHaveBeenCalledWith('availabilityEmails.sent', 4000, { count: 1 });
    expect(notifications.error).toHaveBeenCalledWith('availabilityEmails.failed', 8000, { names: 'Marc Durand' });
    expect([...component.chosen]).toEqual([2]);
    component.toggle(2);
    expect(component.chosen.size).toBe(0);
  });

  it('writes the mission each contact posted and sends a message edited for one of them', () => {
    const { fixture, component, contacts } = create();
    component.subject = 'Disponible';
    component.body = 'Bonjour {{firstName}},\nJ\'ai vu votre mission {{mission}}.\nÀ bientôt';
    component.toggle(1);
    component.toggle(2);
    fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
    fixture.detectChanges();
    const host = fixture.nativeElement as HTMLElement;
    host.querySelector<HTMLButtonElement>('[data-recipient-edit="1"]')!.click();
    fixture.detectChanges();
    expect(component.ownBody(lea)).toBe('Bonjour Léa,\nÀ bientôt');
    expect(host.querySelector('[data-recipient-own="1"]')).not.toBeNull();
    component.setOwn(lea, 'Bonjour Léa, un mot pour vous.');
    component.edit(lea);
    expect(component.editing).toBeNull();
    contacts.sendEmail.and.returnValue(of({} as ContactInteraction));
    component.send();
    expect(contacts.sendEmail.calls.allArgs().map((args: unknown[]) => (args[1] as { body: string }).body)).toEqual(['Bonjour Léa, un mot pour vous.',
      'Bonjour Marc Durand,\nJ\'ai vu votre mission Lead Java.\nÀ bientôt']);
    expect(component.own.size).toBe(0);

    component.setOwn(marc, 'x');
    component.resetOwn(marc);
    expect(component.ownBody(marc)).toContain('Lead Java');
  });

  it('plans the emails over working days, lists those waiting and cancels one', () => {
    const { fixture, component, contacts, notifications } = create();
    component.subject = 'Disponible chez {{company}}';
    component.body = 'Bonjour {{firstName}}';
    component.toggle(1);
    component.toggle(2);
    component.days = 3;
    component.dailyMax = 80;
    const waiting = [{ id: 9, contactId: 1, name: 'Léa Martin', company: 'Conseil', subject: 'Disponible chez Conseil', sendAt: '2026-11-24T08:00:00Z' },
      { id: 10, contactId: 2, name: 'Marc Durand', company: null, subject: 'Disponible chez Autre', sendAt: '2026-11-24T12:00:00Z' }];
    contacts.scheduleEmails.and.returnValue(of(waiting));
    contacts.scheduledEmails.and.returnValue(of(waiting));
    fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-availability-emails-send]')!.textContent).toContain('availabilityEmails.schedule');
    component.send();
    expect(contacts.sendEmail).not.toHaveBeenCalled();
    expect(contacts.scheduleEmails).toHaveBeenCalledWith({
      emails: [{ contactId: 1, subject: 'Disponible chez Conseil', body: 'Bonjour Léa' }, { contactId: 2, subject: 'Disponible chez Autre', body: 'Bonjour Marc Durand' }],
      dossier: true, language: 'FR', days: 3, dailyMax: 50, campaignId: 3
    });
    expect(notifications.success).toHaveBeenCalledWith('availabilityEmails.scheduled', 6000, { count: 2 });
    expect(component.chosen.size).toBe(0);
    fixture.detectChanges();
    const list = (fixture.nativeElement as HTMLElement).querySelector('[data-availability-emails-waiting]')!;
    expect(list.querySelectorAll('li').length).toBe(2);
    expect(list.textContent).toContain('Léa Martin · Conseil');

    contacts.cancelScheduledEmail.and.returnValue(of(undefined));
    component.cancel(component.waiting[0]);
    expect(contacts.cancelScheduledEmail).toHaveBeenCalledWith(9);
    expect(component.waiting.map(email => email.id)).toEqual([10]);
    contacts.cancelScheduledEmail.and.returnValue(throwError(() => new Error('down')));
    component.cancel(component.waiting[0]);
    expect(notifications.error).toHaveBeenCalledWith('availabilityEmails.cancelFailed');

    contacts.scheduleEmails.and.returnValue(throwError(() => new Error('down')));
    component.toggle(1);
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('availabilityEmails.scheduleFailed');
    expect(component.sending).toBeFalse();
    expect([...component.chosen]).toEqual([1]);
  });

  it('starts a campaign for each sending and lists what came of the latest, an email still going when none starts', () => {
    const { fixture, component, contacts, notifications } = create();
    contacts.campaigns.and.returnValue(of([{ id: 3, name: 'Disponible', startedAt: '2026-11-02T08:00:00', sent: 4, waiting: 2, answered: 1,
      opportunities: 1, won: 0, contactIds: [1] }]));
    component.subject = 'Disponible';
    component.body = 'Bonjour';
    component.toggle(1);
    contacts.startCampaign.and.returnValue(throwError(() => new Error('down')));
    contacts.sendEmail.and.returnValue(of({} as ContactInteraction));
    component.send();
    expect(contacts.sendEmail).toHaveBeenCalledWith(1, jasmine.objectContaining({ campaignId: null }));
    expect(contacts.startCampaign).toHaveBeenCalledWith('Disponible');
    fixture.debugElement.injector.get(ChangeDetectorRef).markForCheck();
    fixture.detectChanges();
    const campaign = (fixture.nativeElement as HTMLElement).querySelector('[data-campaign="3"]')!;
    expect(campaign.textContent).toContain('Disponible');
    expect(campaign.textContent).toContain('availabilityEmails.results');
    expect(campaign.textContent).toContain('availabilityEmails.stillWaiting');

    // Prospect again: the contacts it did not reach ticked, its subject back.
    expect(component.newTargets(component.campaigns[0]).map(recipient => recipient.id)).toEqual([2]);
    component.subject = 'Autre';
    (campaign.querySelector('[data-campaign-again="3"]') as HTMLButtonElement).click();
    expect([...component.chosen]).toEqual([2]);
    expect(component.subject).toBe('Disponible');
    expect(notifications.info).toHaveBeenCalledWith('availabilityEmails.againReady', 6000, { count: 1 });

    // Who did not answer two campaigns stays unticked, and is said.
    component.recipients = [lea, { ...marc, silentCampaigns: 2 }];
    component.prospectAgain(component.campaigns[0]);
    expect([...component.chosen]).toEqual([]);
    expect(notifications.info).toHaveBeenCalledWith('availabilityEmails.againReadyLeftOut', 6000, { count: 0, left: 1 });
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
