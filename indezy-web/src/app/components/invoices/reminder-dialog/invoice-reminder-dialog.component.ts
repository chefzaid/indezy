import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { Clipboard } from '@angular/cdk/clipboard';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, forkJoin, map, of } from 'rxjs';

import { ContactDto, ContactEmailStatus, DocumentLanguage, Invoice } from '../../../models';
import { UserProfile } from '../../../models/user-management.models';
import { ContactService } from '../../../services/contact/contact.service';
import { InvoiceService } from '../../../services/invoice/invoice.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { mailtoLink } from '../../../shared/utils/email-template';

/** What a payment reminder says, in the invoice's language; a later one recalls the day of the last. */
export function reminderText(invoice: Invoice, firstName: string | null | undefined, signature: string): { subject: string; body: string } {
  const fees = invoice.lateFees;
  const en = invoice.language === 'EN';
  const locale = en ? 'en-GB' : 'fr-FR';
  const money = (amount: number): string => new Intl.NumberFormat(locale, { style: 'currency', currency: invoice.currency ?? 'EUR' }).format(amount);
  const euros = (amount: number): string => new Intl.NumberFormat(locale, { style: 'currency', currency: 'EUR' }).format(amount);
  const day = (iso: string | null): string => iso ? new Intl.DateTimeFormat(locale, { dateStyle: 'long' }).format(new Date(`${iso}T12:00:00`)) : '';
  const number = invoice.number ?? '';
  const due = money(fees?.amountDue ?? invoice.total);
  const late = fees?.daysLate ?? 0;
  const greeting = en ? `Hello${firstName ? ' ' + firstName : ''},` : `Bonjour${firstName ? ' ' + firstName : ''},`;
  const penalties = fees && fees.rate !== null && fees.interest !== null
    ? (en ? `late payment interest at ${fees.rate}% a year, ${money(fees.interest)} to date, and the fixed ${euros(fees.indemnity)} recovery indemnity`
      : `des pénalités au taux de ${String(fees.rate).replace('.', ',')} % l'an, soit ${money(fees.interest)} à ce jour, et l'indemnité forfaitaire de ${euros(fees.indemnity)} pour frais de recouvrement`)
    : (en ? `the fixed ${euros(fees?.indemnity ?? 40)} recovery indemnity` : `l'indemnité forfaitaire de ${euros(fees?.indemnity ?? 40)} pour frais de recouvrement`);
  const again = invoice.remindedOn ?? null;
  if (en) {
    return {
      subject: again ? `Invoice ${number}: second reminder` : `Invoice ${number}: payment pending`,
      body: `${greeting}\n\n` + (again ? `I am following up on my reminder of ${day(again)}: invoice ${number} of ${day(invoice.issueDate)}, for ${due}, ` +
        `due on ${day(invoice.dueDate)}, is still unpaid (${late} days late).` : `Unless I am mistaken, invoice ${number} of ${day(invoice.issueDate)}, ` +
        `for ${due}, was due on ${day(invoice.dueDate)} and is still unpaid (${late} days late).`) +
        `\n\nCould you tell me when it will be paid?\n\nAs a reminder, late payment brings ${penalties} ` +
        `(French Commercial Code, articles L441-10 and D441-5).\n\nKind regards,\n${signature}`.trimEnd()
    };
  }
  return {
    subject: again ? `Facture ${number} : seconde relance` : `Facture ${number} : paiement en attente`,
    body: `${greeting}\n\n` + (again ? `Je reviens vers vous après ma relance du ${day(again)} : la facture ${number} du ${day(invoice.issueDate)}, ` +
      `d'un montant de ${due}, arrivée à échéance le ${day(invoice.dueDate)}, reste impayée (${late} jours de retard).` :
      `Sauf erreur de ma part, la facture ${number} du ${day(invoice.issueDate)}, d'un montant de ${due}, arrivée à échéance ` +
      `le ${day(invoice.dueDate)}, reste impayée (${late} jours de retard).`) + `\n\nPourriez-vous m'indiquer la date prévue de son règlement ?\n\n` +
      `Pour rappel, tout retard de paiement entraîne ${penalties} (art. L441-10 et D441-5 du Code de commerce).\n\nBien cordialement,\n${signature}`.trimEnd()
  };
}

/**
 * Reminds a client of an overdue invoice (see docs/features.md#invoices): the reminder written in the invoice's language
 * with the amount left to pay, the days late, the interest at the rate it stated and the €40 indemnity, for one of the
 * client's contacts; opened in the mail app, copied, or sent from Indezy and recorded in the contact's exchanges, the
 * invoice then noting the day (closes with the invoice so).
 */
@Component({
  selector: 'app-invoice-reminder-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'invoices.reminder.title' | translate:{ number: invoice.number } }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="reminder-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'emails.recipient' | translate }}</mat-label>
          <mat-select formControlName="recipient" (selectionChange)="write()" data-reminder-recipient>
            @for (contact of contacts; track contact.id) {
              <mat-option [value]="contact.id">{{ contact.firstName }} {{ contact.lastName }} · {{ contact.email }}</mat-option>
            }
            <mat-option [value]="null">{{ 'emails.noRecipient' | translate }}</mat-option>
          </mat-select>
        </mat-form-field>
        @if (loaded && !contacts.length) {
          <p class="hint" role="status" data-reminder-no-contact>{{ 'invoices.reminder.noContact' | translate }}</p>
        }
        <mat-form-field appearance="outline">
          <mat-label>{{ 'emails.subject' | translate }}</mat-label>
          <input matInput formControlName="subject" data-reminder-subject>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'emails.body' | translate }}</mat-label>
          <textarea matInput formControlName="body" rows="12" data-reminder-body></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="copy()" data-reminder-copy><mat-icon>content_copy</mat-icon>{{ 'emails.copy' | translate }}</button>
      <a mat-stroked-button [href]="mailto" data-reminder-open><mat-icon>open_in_new</mat-icon>{{ 'emails.open' | translate }}</a>
      @if (status?.available) {
        <button mat-flat-button color="primary" type="button" (click)="send()" [disabled]="!canSend" data-reminder-send>
          <mat-icon>send</mat-icon>{{ 'emails.send.button' | translate }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .reminder-form { display: flex; flex-direction: column; min-width: min(560px, 85vw); padding-top: 4px; }
    .hint { margin: -8px 0 12px; color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class InvoiceReminderDialogComponent implements OnInit {
  readonly invoice = inject<Invoice>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<InvoiceReminderDialogComponent>, { optional: true });
  private readonly contactService = inject(ContactService);
  private readonly invoices = inject(InvoiceService);
  private readonly users = inject(UserManagementService);
  private readonly notifications = inject(NotificationService);
  private readonly clipboard = inject(Clipboard);
  private readonly changes = inject(ChangeDetectorRef);

  readonly form = inject(FormBuilder).nonNullable.group({ recipient: [null as number | null], subject: [''], body: [''] });
  contacts: ContactDto[] = [];
  status: ContactEmailStatus | null = null;
  loaded = false;
  sending = false;
  private profile: UserProfile | null = null;

  ngOnInit(): void {
    this.write();
    forkJoin({
      contacts: this.contactService.getByClientId(this.invoice.clientId).pipe(catchError(() => of([] as ContactDto[]))),
      status: this.contactService.emailStatus().pipe(catchError(() => of(null))),
      profile: this.users.getUserProfile().pipe(map((profile): UserProfile | null => profile), catchError(() => of(null)))
    }).subscribe(({ contacts, status, profile }) => {
      this.contacts = contacts.filter(contact => !!contact.email);
      this.status = status;
      this.profile = profile;
      this.loaded = true;
      this.form.patchValue({ recipient: this.contacts[0]?.id ?? null });
      this.write();
      this.changes.markForCheck();
    });
  }

  /** Writes the reminder for the chosen recipient, over what was typed. */
  write(): void {
    const recipient = this.recipient();
    const signature = [this.profile?.firstName, this.profile?.lastName].filter(Boolean).join(' ');
    this.form.patchValue(reminderText(this.invoice, recipient?.firstName, signature));
  }

  get mailto(): string {
    const { subject, body } = this.form.getRawValue();
    return mailtoLink(this.recipient()?.email, subject, body);
  }

  get canSend(): boolean {
    const { subject, body } = this.form.getRawValue();
    return !!this.status?.available && !!this.recipient() && !this.sending && !!subject.trim() && !!body.trim();
  }

  copy(): void {
    const { subject, body } = this.form.getRawValue();
    if (this.clipboard.copy(`${subject}\n\n${body}`)) {
      this.notifications.success('emails.copied');
    } else {
      this.notifications.error('emails.copyError');
    }
  }

  /** Sends the reminder from Indezy to the contact, recorded in their exchanges, with a copy to the freelancer. */
  send(): void {
    const recipient = this.recipient();
    if (!recipient?.id || !this.canSend) {
      return;
    }
    this.sending = true;
    const { subject, body } = this.form.getRawValue();
    const language: DocumentLanguage = this.invoice.language === 'EN' ? 'EN' : 'FR';
    this.contactService.sendEmail(recipient.id, { projectId: this.invoice.projectId, subject, body, track: false, copy: true, language }).subscribe({
      next: () => {
        this.sending = false;
        this.notifications.success('emails.send.sent', undefined, { name: `${recipient.firstName ?? ''} ${recipient.lastName ?? ''}`.trim() });
        // Sent: the invoice says when its client was reminded (left unsaid if that fails, the email being gone).
        this.invoices.reminded(this.invoice.id).subscribe({
          next: invoice => this.dialogRef?.close(invoice),
          error: () => this.dialogRef?.close(true)
        });
      },
      error: (error: HttpErrorResponse) => {
        this.sending = false;
        const message: string = error.error?.message ?? '';
        this.notifications.error(message.startsWith('Up to') ? 'emails.send.dailyLimit' : 'emails.send.error');
        this.changes.markForCheck();
      }
    });
  }

  private recipient(): ContactDto | undefined {
    return this.contacts.find(contact => contact.id === this.form.getRawValue().recipient);
  }
}
