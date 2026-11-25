import { TestBed } from '@angular/core/testing';
import { Clipboard } from '@angular/cdk/clipboard';
import { HttpErrorResponse } from '@angular/common/http';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';

import { ContactDto, ContactInteraction, Invoice } from '../../../models';
import { UserProfile } from '../../../models/user-management.models';
import { ContactService } from '../../../services/contact/contact.service';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { InvoiceReminderDialogComponent, reminderText } from './invoice-reminder-dialog.component';

describe('InvoiceReminderDialogComponent', () => {
  const invoice = {
    id: 9, kind: 'INVOICE', number: 'F2026-0007', status: 'ISSUED', overdue: true, clientId: 3, clientName: 'Acme', projectId: 42,
    issueDate: '2026-10-01', dueDate: '2026-10-31', language: 'FR', total: 7800, currency: 'EUR',
    lateFees: { daysLate: 25, amountDue: 7800, rate: 10.5, interest: 56.1, indemnity: 40 }
  } as unknown as Invoice;
  const lea = { id: 11, firstName: 'Léa', lastName: 'Martin', email: 'lea@acme.fr' } as ContactDto;
  let contacts: jasmine.SpyObj<ContactService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<InvoiceReminderDialogComponent>>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  let invoices: jasmine.SpyObj<InvoiceService>;

  function create(data: Invoice = invoice, found: ContactDto[] = [lea, { id: 12, firstName: 'Sans', email: null } as ContactDto], available = true) {
    contacts = jasmine.createSpyObj('ContactService', ['getByClientId', 'emailStatus', 'sendEmail']);
    contacts.getByClientId.and.returnValue(of(found));
    contacts.emailStatus.and.returnValue(of({ available, sentToday: 0, dailyMax: 50 }));
    const users = jasmine.createSpyObj('UserManagementService', ['getUserProfile']);
    users.getUserProfile.and.returnValue(of({ firstName: 'Ada', lastName: 'Lovelace' } as UserProfile));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    invoices = jasmine.createSpyObj('InvoiceService', ['reminded']);
    invoices.reminded.and.returnValue(of({ ...data, remindedOn: '2026-11-25' } as Invoice));
    TestBed.configureTestingModule({
      imports: [InvoiceReminderDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: ContactService, useValue: contacts }, { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications }, { provide: MatDialogRef, useValue: dialogRef },
        { provide: Clipboard, useValue: clipboard }, { provide: InvoiceService, useValue: invoices }]
    });
    const fixture = TestBed.createComponent(InvoiceReminderDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('writes the reminder in the invoice\'s language, with the interest at its rate and the indemnity', () => {
    // Numbers and dates spaced as the locale spaces them (narrow no-break spaces), read here as plain ones.
    const plain = (text: { subject: string; body: string }) => ({ subject: text.subject, body: text.body.replace(/[\u202f\u00a0]/g, ' ') });
    const fr = plain(reminderText(invoice, 'Léa', 'Ada Lovelace'));
    expect(fr.subject).toBe('Facture F2026-0007 : paiement en attente');
    expect(fr.body).toContain('Bonjour Léa,');
    expect(fr.body).toContain('la facture F2026-0007 du 1 octobre 2026');
    expect(fr.body).toContain('7 800,00');
    expect(fr.body).toContain('31 octobre 2026');
    expect(fr.body).toContain('25 jours de retard');
    expect(fr.body).toContain("au taux de 10,5 % l'an, soit 56,10");
    expect(fr.body).toContain('40,00');
    expect(fr.body).toContain('art. L441-10 et D441-5 du Code de commerce');
    expect(fr.body.endsWith('Ada Lovelace')).toBeTrue();

    // In English, in dollars, and with no rate stated: the indemnity only.
    const en = plain(reminderText({ ...invoice, language: 'EN', currency: 'USD', lateFees: { ...invoice.lateFees!, rate: null, interest: null } } as Invoice,
      null, ''));
    expect(en.subject).toBe('Invoice F2026-0007: payment pending');
    expect(en.body).toContain('Hello,');
    expect(en.body).toContain('US$7,800.00');
    expect(en.body).toContain('the fixed €40.00 recovery indemnity');
    expect(en.body).not.toContain('interest');
    expect(en.body.endsWith('Kind regards,')).toBeTrue();

    // Reminded before: the next one says so, with the day.
    const second = plain(reminderText({ ...invoice, remindedOn: '2026-11-10' } as Invoice, 'Léa', 'Ada'));
    expect(second.subject).toBe('Facture F2026-0007 : seconde relance');
    expect(second.body).toContain('après ma relance du 10 novembre 2026');
    const secondEn = plain(reminderText({ ...invoice, language: 'EN', remindedOn: '2026-11-10' } as Invoice, null, 'Ada'));
    expect(secondEn.subject).toBe('Invoice F2026-0007: second reminder');
    expect(secondEn.body).toContain('my reminder of 10 November 2026');
  });

  it('writes to the client\'s first contact with an email, and sends it from Indezy with a copy', () => {
    const fixture = create();
    const component = fixture.componentInstance;
    expect(component.contacts.map(contact => contact.id)).toEqual([11]);
    expect(component.form.getRawValue().recipient).toBe(11);
    expect(component.form.getRawValue().body).toContain('Bonjour Léa,');
    expect(component.mailto).toMatch(/^mailto:lea@acme\.fr\?subject=Facture%20F2026-0007/);

    contacts.sendEmail.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409, error: { message: 'Up to 50 emails a day' } })));
    component.send();
    expect(notifications.error).toHaveBeenCalledWith('emails.send.dailyLimit');
    contacts.sendEmail.and.returnValue(of({} as ContactInteraction));
    component.send();
    expect(contacts.sendEmail).toHaveBeenCalledWith(11, jasmine.objectContaining({ projectId: 42, track: false, copy: true, language: 'FR',
      subject: 'Facture F2026-0007 : paiement en attente' }));
    // Sent: the invoice notes the day, and the list gets it back.
    expect(invoices.reminded).toHaveBeenCalledWith(9);
    expect(dialogRef.close).toHaveBeenCalledWith(jasmine.objectContaining({ id: 9, remindedOn: '2026-11-25' }));
    invoices.reminded.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.send();
    expect(dialogRef.close).toHaveBeenCalledWith(true);

    clipboard.copy.and.returnValue(true);
    component.copy();
    expect(notifications.success).toHaveBeenCalledWith('emails.copied');
    clipboard.copy.and.returnValue(false);
    component.copy();
    expect(notifications.error).toHaveBeenCalledWith('emails.copyError');
  });

  it('says when the client has no contact to write to, and sends nothing without a recipient or with email off', () => {
    let fixture = create(invoice, []);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-reminder-no-contact]')).not.toBeNull();
    expect(fixture.componentInstance.canSend).toBeFalse();
    fixture.componentInstance.send();
    expect(contacts.sendEmail).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    fixture = create(invoice, [lea], false);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-reminder-send]')).toBeNull();
    expect(fixture.componentInstance.canSend).toBeFalse();
  });
});
