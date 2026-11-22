import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { concatMap, from, map, of, catchError, toArray } from 'rxjs';
import { AvailabilityRecipient, DocumentLanguage } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * Tells the ESN contacts worked with of an availability (see docs/features.md#emails-sent-from-indezy): the contacts of
 * the ESNs the workspace had an opportunity with, none ticked at first; one email each from Indezy, `{{firstName}}`
 * replaced by theirs, with the dossier de compétences attached unless unticked, each recorded in their exchanges.
 */
@Component({
  selector: 'app-availability-emails',
  imports: [FormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressSpinnerModule,
    TranslateModule],
  template: `
    <details class="emails" (toggle)="opened($event)" data-availability-emails>
      <summary>{{ 'availabilityEmails.title' | translate }}</summary>
      @if (recipients === null) {
        <mat-spinner diameter="24" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
      } @else if (!recipients.length) {
        <p class="hint" data-availability-emails-none>{{ 'availabilityEmails.none' | translate }}</p>
      } @else {
        <p class="hint">{{ 'availabilityEmails.hint' | translate }}</p>
        <ul class="recipients">
          @for (recipient of recipients; track recipient.id) {
            <li>
              <mat-checkbox [checked]="chosen.has(recipient.id)" (change)="toggle(recipient.id)" [attr.data-availability-recipient]="recipient.id">
                {{ recipient.name }} <span class="company">· {{ recipient.company }}</span>
              </mat-checkbox>
            </li>
          }
        </ul>
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'availabilityEmails.subject' | translate }}</mat-label>
          <input matInput [(ngModel)]="subject" maxlength="200" data-availability-emails-subject>
        </mat-form-field>
        <mat-form-field appearance="outline" class="full">
          <mat-label>{{ 'availabilityEmails.body' | translate }}</mat-label>
          <textarea matInput [(ngModel)]="body" rows="8" maxlength="20000" data-availability-emails-body></textarea>
          <mat-hint>{{ 'availabilityEmails.variable' | translate }}</mat-hint>
        </mat-form-field>
        <mat-checkbox [(ngModel)]="dossier" data-availability-emails-dossier>{{ 'availabilityEmails.dossier' | translate }}</mat-checkbox>
        <div class="row">
          <button mat-flat-button color="primary" type="button" (click)="send()" data-availability-emails-send
            [disabled]="sending || !chosen.size || !subject.trim() || !body.trim()">
            @if (sending) {
              <mat-spinner diameter="18" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
            } @else {
              <mat-icon>send</mat-icon>
            }
            {{ 'availabilityEmails.send' | translate:{ count: chosen.size } }}
          </button>
        </div>
      }
    </details>
  `,
  styles: [`
    .emails { margin-top: 16px; }
    summary { cursor: pointer; font-weight: 500; padding: 8px 0; }
    .hint { color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
    .recipients { list-style: none; margin: 0 0 12px; padding: 0; }
    .company { color: var(--mat-sys-on-surface-variant); }
    .full { width: 100%; }
    .row { display: flex; justify-content: flex-end; margin-top: 8px; }
    mat-spinner { display: inline-block; margin-right: 8px; }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AvailabilityEmailsComponent implements OnInit {
  @Input() language: DocumentLanguage = 'FR';
  /** The day the workspace is available from, for the subject. */
  @Input() availableFrom: string | null = null;
  /** What the announcement says, put after the greeting when the section opens. */
  @Input() text = '';

  private readonly contacts = inject(ContactService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  recipients: AvailabilityRecipient[] | null = null;
  readonly chosen = new Set<number>();
  subject = '';
  body = '';
  dossier = true;
  sending = false;

  ngOnInit(): void {
    this.contacts.availabilityRecipients().subscribe({
      next: recipients => {
        this.recipients = recipients;
        this.changes.markForCheck();
      },
      error: () => {
        this.recipients = [];
        this.changes.markForCheck();
      }
    });
  }

  /** Writes the subject and the message once, as the section opens, from the announcement if written. */
  opened(event: Event): void {
    if (!(event.target as HTMLDetailsElement).open || this.body) {
      return;
    }
    const english = this.language === 'EN';
    const from = this.availableFrom ? new Date(this.availableFrom + 'T00:00:00').toLocaleDateString(english ? 'en-GB' : 'fr-FR',
      { day: 'numeric', month: 'long', year: 'numeric' }) : null;
    this.subject = from ? (english ? `Available from ${from}` : `Disponible à partir du ${from}`) : (english ? 'My availability' : 'Ma disponibilité');
    this.body = (english ? 'Hello {{firstName}},\n\n' : 'Bonjour {{firstName}},\n\n') + (this.text.trim() ||
      this.translate.instant('availabilityEmails.defaultText')) + (this.dossier ? '\n\n' + this.translate.instant('availabilityEmails.attached') : '');
    this.changes.markForCheck();
  }

  toggle(id: number): void {
    if (this.chosen.has(id)) {
      this.chosen.delete(id);
    } else {
      this.chosen.add(id);
    }
  }

  /** One email to each contact ticked, one after the other; those that could not go are said and stay ticked. */
  send(): void {
    const recipients = (this.recipients ?? []).filter(recipient => this.chosen.has(recipient.id));
    if (!recipients.length || this.sending) {
      return;
    }
    this.sending = true;
    from(recipients).pipe(
      concatMap(recipient => this.contacts.sendEmail(recipient.id, {
        subject: this.subject.trim(), body: this.body.replaceAll('{{firstName}}', recipient.firstName?.trim() || recipient.name),
        track: false, copy: false, language: this.language, dossier: this.dossier
      }).pipe(map(() => ({ recipient, sent: true })), catchError(() => of({ recipient, sent: false })))),
      toArray()
    ).subscribe(results => {
      this.sending = false;
      const sent = results.filter(result => result.sent);
      sent.forEach(result => this.chosen.delete(result.recipient.id));
      const failed = results.filter(result => !result.sent).map(result => result.recipient.name);
      if (sent.length) {
        this.notifications.success('availabilityEmails.sent', 4000, { count: sent.length });
      }
      if (failed.length) {
        this.notifications.error('availabilityEmails.failed', 8000, { names: failed.join(', ') });
      }
      this.changes.markForCheck();
    });
  }
}
