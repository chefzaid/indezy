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
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { ClientEmailDialogComponent, ClientEmailDialogData } from './client-email-dialog.component';
import { reminderText } from '../../../components/invoices/payment-reminder/payment-reminder';

describe('ClientEmailDialogComponent', () => {
  const invoice = {
    id: 9, kind: 'INVOICE', number: 'F2026-0007', status: 'ISSUED', overdue: true, clientId: 3, clientName: 'Acme', projectId: 42,
    issueDate: '2026-10-01', dueDate: '2026-10-31', language: 'FR', total: 7800, currency: 'EUR',
    lateFees: { daysLate: 25, amountDue: 7800, rate: 10.5, interest: 56.1, indemnity: 40 }
  } as unknown as Invoice;
  const lea = { id: 11, firstName: 'Léa', lastName: 'Martin', email: 'lea@acme.fr' } as ContactDto;
  let contacts: jasmine.SpyObj<ContactService>;
  let notifications: jasmine.SpyObj<NotificationService>;
  let dialogRef: jasmine.SpyObj<MatDialogRef<ClientEmailDialogComponent>>;
  let clipboard: jasmine.SpyObj<Clipboard>;
  let sent: jasmine.Spy;

  function create(data: Invoice = invoice, found: ContactDto[] = [lea, { id: 12, firstName: 'Sans', email: null } as ContactDto], available = true,
                  prefer?: RegExp) {
    contacts = jasmine.createSpyObj('ContactService', ['getByClientId', 'emailStatus', 'sendEmail']);
    contacts.getByClientId.and.returnValue(of(found));
    contacts.emailStatus.and.returnValue(of({ available, sentToday: 0, dailyMax: 50 }));
    const users = jasmine.createSpyObj('UserManagementService', ['getUserProfile']);
    users.getUserProfile.and.returnValue(of({ firstName: 'Ada', lastName: 'Lovelace' } as UserProfile));
    notifications = jasmine.createSpyObj('NotificationService', ['success', 'error']);
    dialogRef = jasmine.createSpyObj('MatDialogRef', ['close']);
    clipboard = jasmine.createSpyObj('Clipboard', ['copy']);
    sent = jasmine.createSpy('sent').and.returnValue(of({ ...data, remindedOn: '2026-11-25' } as Invoice));
    const dialogData: ClientEmailDialogData = { title: 'Relance de paiement de F2026-0007', clientId: data.clientId, projectId: data.projectId,
      language: data.language, write: (firstName, signature) => reminderText(data, firstName, signature), sent, prefer };
    TestBed.configureTestingModule({
      imports: [ClientEmailDialogComponent, TranslateModule.forRoot()],
      providers: [{ provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } }, { provide: MAT_DIALOG_DATA, useValue: dialogData },
        { provide: ContactService, useValue: contacts }, { provide: UserManagementService, useValue: users },
        { provide: NotificationService, useValue: notifications }, { provide: MatDialogRef, useValue: dialogRef },
        { provide: Clipboard, useValue: clipboard }]
    });
    const fixture = TestBed.createComponent(ClientEmailDialogComponent);
    fixture.detectChanges();
    return fixture;
  }

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
    // Sent: what the caller records, and the dialog closes with its answer.
    expect(sent).toHaveBeenCalled();
    expect(dialogRef.close).toHaveBeenCalledWith(jasmine.objectContaining({ id: 9, remindedOn: '2026-11-25' }));
    sent.and.returnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
    component.send();
    expect(dialogRef.close).toHaveBeenCalledWith(true);

    clipboard.copy.and.returnValue(true);
    component.copy();
    expect(notifications.success).toHaveBeenCalledWith('emails.copied');
    clipboard.copy.and.returnValue(false);
    component.copy();
    expect(notifications.error).toHaveBeenCalledWith('emails.copyError');
  });

  it('picks first a contact the caller prefers, else the first with an email', () => {
    const compta = { id: 14, firstName: 'Service', lastName: 'Comptabilité', email: 'compta@acme.fr' } as ContactDto;
    const fixture = create(invoice, [lea, compta], true, /compta/i);
    expect(fixture.componentInstance.form.getRawValue().recipient).toBe(14);
  });

  it('says when the client has no contact to write to, and sends nothing without a recipient or with email off', () => {
    let fixture = create(invoice, []);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-client-email-no-contact]')).not.toBeNull();
    expect(fixture.componentInstance.canSend).toBeFalse();
    fixture.componentInstance.send();
    expect(contacts.sendEmail).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    fixture = create(invoice, [lea], false);
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-client-email-send]')).toBeNull();
    expect(fixture.componentInstance.canSend).toBeFalse();
  });
});
