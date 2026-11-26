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
import { Observable, catchError, forkJoin, map, of } from 'rxjs';

import { ContactDto, ContactEmailStatus, DocumentLanguage } from '../../../models';
import { UserProfile } from '../../../models/user-management.models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { mailtoLink } from '../../utils/email-template';

/** An email to one of a client's contacts, written by its caller for the contact chosen. */
export interface ClientEmailDialogData {
  /** The title, translated. */
  title: string;
  clientId: number;
  projectId: number | null;
  /** The language the email is written in (that of its document). */
  language: DocumentLanguage;
  /** Writes the email for the contact's first name, signed. */
  write: (firstName: string | null | undefined, signature: string) => { subject: string; body: string };
  /** What to record once it was sent from Indezy, the dialog then closing with its answer (or true when it fails). */
  sent?: () => Observable<unknown>;
}

/**
 * An email about a document to one of its client's contacts with an email (a payment reminder, a quote's follow-up, see
 * docs/features.md#invoices): written by the caller for the contact chosen, opened in the mail app, copied, or sent from
 * Indezy with a copy to the freelancer and recorded in the contact's exchanges.
 */
@Component({
  selector: 'app-client-email-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <form [formGroup]="form" class="client-email-form">
        <mat-form-field appearance="outline">
          <mat-label>{{ 'emails.recipient' | translate }}</mat-label>
          <mat-select formControlName="recipient" (selectionChange)="write()" data-client-email-recipient>
            @for (contact of contacts; track contact.id) {
              <mat-option [value]="contact.id">{{ contact.firstName }} {{ contact.lastName }} · {{ contact.email }}</mat-option>
            }
            <mat-option [value]="null">{{ 'emails.noRecipient' | translate }}</mat-option>
          </mat-select>
        </mat-form-field>
        @if (loaded && !contacts.length) {
          <p class="hint" role="status" data-client-email-no-contact>{{ 'emails.noClientContact' | translate }}</p>
        }
        <mat-form-field appearance="outline">
          <mat-label>{{ 'emails.subject' | translate }}</mat-label>
          <input matInput formControlName="subject" data-client-email-subject>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>{{ 'emails.body' | translate }}</mat-label>
          <textarea matInput formControlName="body" rows="12" data-client-email-body></textarea>
        </mat-form-field>
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="copy()" data-client-email-copy><mat-icon>content_copy</mat-icon>{{ 'emails.copy' | translate }}</button>
      <a mat-stroked-button [href]="mailto" data-client-email-open><mat-icon>open_in_new</mat-icon>{{ 'emails.open' | translate }}</a>
      @if (status?.available) {
        <button mat-flat-button color="primary" type="button" (click)="send()" [disabled]="!canSend" data-client-email-send>
          <mat-icon>send</mat-icon>{{ 'emails.send.button' | translate }}
        </button>
      }
    </mat-dialog-actions>
  `,
  styles: [`
    .client-email-form { display: flex; flex-direction: column; min-width: min(560px, 85vw); padding-top: 4px; }
    .hint { margin: -8px 0 12px; color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ClientEmailDialogComponent implements OnInit {
  readonly data = inject<ClientEmailDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ClientEmailDialogComponent>, { optional: true });
  private readonly contactService = inject(ContactService);
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
      contacts: this.contactService.getByClientId(this.data.clientId).pipe(catchError(() => of([] as ContactDto[]))),
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

  /** Writes the email for the chosen recipient, over what was typed. */
  write(): void {
    const recipient = this.recipient();
    const signature = [this.profile?.firstName, this.profile?.lastName].filter(Boolean).join(' ');
    this.form.patchValue(this.data.write(recipient?.firstName, signature));
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

  /** Sends the email from Indezy to the contact, recorded in their exchanges, with a copy to the freelancer. */
  send(): void {
    const recipient = this.recipient();
    if (!recipient?.id || !this.canSend) {
      return;
    }
    this.sending = true;
    const { subject, body } = this.form.getRawValue();
    const language: DocumentLanguage = this.data.language === 'EN' ? 'EN' : 'FR';
    this.contactService.sendEmail(recipient.id, { projectId: this.data.projectId, subject, body, track: false, copy: true, language }).subscribe({
      next: () => {
        this.sending = false;
        this.notifications.success('emails.send.sent', undefined, { name: `${recipient.firstName ?? ''} ${recipient.lastName ?? ''}`.trim() });
        // Sent: the caller records it (left unsaid if that fails, the email being gone).
        if (!this.data.sent) {
          this.dialogRef?.close(true);
          return;
        }
        this.data.sent().subscribe({
          next: answer => this.dialogRef?.close(answer),
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
