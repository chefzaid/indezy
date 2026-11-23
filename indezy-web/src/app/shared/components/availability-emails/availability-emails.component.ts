import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { DatePipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, concatMap, from, map, of, catchError, switchMap, toArray } from 'rxjs';
import { AvailabilityRecipient, DocumentLanguage, ProspectionCampaign, ScheduledEmail } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';

/**
 * Tells the ESN contacts of an availability (see docs/features.md#emails-sent-from-indezy): the contacts of the ESNs the
 * workspace had an opportunity with or heard from, or that posted missions for the profile in the feed in the last 60
 * days, not those in touch in the last six weeks nor those who asked not to be contacted, those who answered most first
 * then those posting most, none ticked at first; one email each from Indezy, `{{firstName}}`, `{{company}}` and `{{mission}}` replaced by theirs (or a message written for one contact), with the dossier de compétences attached unless unticked, each recorded in their exchanges.
 */
/** Contacts this many campaigns wrote to without an answer are not ticked again by **Prospect again**. */
export const SILENT_LIMIT = 2;

@Component({
  selector: 'app-availability-emails',
  imports: [FormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule, MatIconModule, MatInputModule, MatProgressSpinnerModule,
    MatSelectModule, DatePipe, TranslateModule],
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
          @for (recipient of shownRecipients(); track recipient.id) {
            <li>
              <mat-checkbox [checked]="chosen.has(recipient.id)" (change)="toggle(recipient.id)" [attr.data-availability-recipient]="recipient.id">
                {{ recipient.name }} <span class="company">· {{ recipient.company }}
                  @if (recipient.answers) {
                    · {{ 'availabilityEmails.answers' | translate:{ count: recipient.answers } }}
                  }
                  @if (recipient.postings) {
                    · <span data-recipient-postings>{{ 'availabilityEmails.postings' | translate:{ count: recipient.postings } }}</span>
                  }
                  @if (recipient.ratePaid) {
                    · <span data-recipient-rate-paid>{{ 'availabilityEmails.ratePaid' | translate:{ rate: recipient.ratePaid } }}</span>
                  }
                  @if (recipient.campaignOpportunities) {
                    · <span data-recipient-campaign-opportunities>{{ 'availabilityEmails.campaignOpportunities' | translate:{ count: recipient.campaignOpportunities } }}</span>
                  }
                  @if (recipient.silentCampaigns) {
                    · <span data-recipient-silent>{{ 'availabilityEmails.silentCampaigns' | translate:{ count: recipient.silentCampaigns } }}</span>
                  }
                </span>
              </mat-checkbox>
              @if (chosen.has(recipient.id)) {
                <button mat-button type="button" (click)="edit(recipient)" [attr.data-recipient-edit]="recipient.id"
                  [attr.aria-expanded]="editing === recipient.id">
                  {{ (own.has(recipient.id) ? 'availabilityEmails.edited' : 'availabilityEmails.edit') | translate }}
                </button>
              }
              @if (editing === recipient.id) {
                <div class="own" [attr.data-recipient-own]="recipient.id">
                  <mat-form-field appearance="outline" class="full">
                    <mat-label>{{ 'availabilityEmails.ownBody' | translate:{ name: recipient.name } }}</mat-label>
                    <textarea matInput rows="6" maxlength="20000" [ngModel]="ownBody(recipient)" (ngModelChange)="setOwn(recipient, $event)"
                      data-recipient-own-body></textarea>
                  </mat-form-field>
                  @if (own.has(recipient.id)) {
                    <button mat-button type="button" (click)="resetOwn(recipient)" data-recipient-own-reset>
                      {{ 'availabilityEmails.reset' | translate }}
                    </button>
                  }
                </div>
              }
            </li>
          }
        </ul>
        @if (recipients.length > cap && !showAll) {
          <button mat-button type="button" (click)="showAll = true" data-availability-emails-show-all>
            {{ 'availabilityEmails.showAll' | translate:{ count: recipients.length } }}
          </button>
        }
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
        <div class="when">
          <mat-form-field appearance="outline">
            <mat-label>{{ 'availabilityEmails.when' | translate }}</mat-label>
            <mat-select [(ngModel)]="days" data-availability-emails-days>
              <mat-option [value]="0">{{ 'availabilityEmails.now' | translate }}</mat-option>
              @for (count of spreads; track count) {
                <mat-option [value]="count">{{ 'availabilityEmails.over' | translate:{ count } }}</mat-option>
              }
            </mat-select>
            <mat-hint>{{ (days ? 'availabilityEmails.spreadHint' : 'availabilityEmails.nowHint') | translate }}</mat-hint>
          </mat-form-field>
          @if (days) {
            <mat-form-field appearance="outline">
              <mat-label>{{ 'availabilityEmails.dailyMax' | translate }}</mat-label>
              <input matInput type="number" min="1" max="50" [(ngModel)]="dailyMax" data-availability-emails-daily-max>
            </mat-form-field>
          }
        </div>
        <div class="row">
          <button mat-stroked-button type="button" (click)="writeEach()" data-availability-emails-ai
            [disabled]="writing || sending || !chosen.size || chosen.size > maxWritten || !body.trim()"
            [attr.aria-describedby]="chosen.size > maxWritten ? 'availability-emails-ai-max' : null">
            @if (writing) {
              <mat-spinner diameter="18" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
            } @else {
              <mat-icon>auto_awesome</mat-icon>
            }
            {{ 'availabilityEmails.writeEach' | translate:{ count: chosen.size } }}
          </button>
          @if (chosen.size > maxWritten) {
            <span class="hint" id="availability-emails-ai-max">{{ 'availabilityEmails.writeEachMax' | translate:{ max: maxWritten } }}</span>
          }
          <button mat-flat-button color="primary" type="button" (click)="send()" data-availability-emails-send
            [disabled]="sending || !chosen.size || !subject.trim() || !body.trim()">
            @if (sending) {
              <mat-spinner diameter="18" [attr.aria-label]="'common.loading' | translate"></mat-spinner>
            } @else {
              <mat-icon>send</mat-icon>
            }
            {{ (days ? 'availabilityEmails.schedule' : 'availabilityEmails.send') | translate:{ count: chosen.size } }}
          </button>
        </div>
      }
      @if (campaigns.length) {
        <h4 class="waiting-title">{{ 'availabilityEmails.campaigns' | translate }}</h4>
        <ul class="campaigns" data-availability-campaigns>
          @for (campaign of campaigns; track campaign.id) {
            <li [attr.data-campaign]="campaign.id">
              <span class="name">{{ campaign.name }} · {{ campaign.startedAt | date:'d MMM y' }}</span>
              <span class="results">{{ 'availabilityEmails.results' | translate:{ sent: campaign.sent, answered: campaign.answered,
                opportunities: campaign.opportunities, won: campaign.won } }}
                @if (campaign.waiting) {
                  · {{ 'availabilityEmails.stillWaiting' | translate:{ count: campaign.waiting } }}
                }
              </span>
              @if (newTargets(campaign).length) {
                <button mat-button type="button" (click)="prospectAgain(campaign)" [attr.data-campaign-again]="campaign.id">
                  <mat-icon>replay</mat-icon>{{ 'availabilityEmails.again' | translate:{ count: newTargets(campaign).length } }}
                </button>
              }
            </li>
          }
        </ul>
      }
      @if (waiting.length) {
        <h4 class="waiting-title">{{ 'availabilityEmails.waiting' | translate:{ count: waiting.length } }}</h4>
        <ul class="waiting" data-availability-emails-waiting>
          @for (email of waiting; track email.id) {
            <li [attr.data-scheduled-email]="email.id">
              <span>{{ email.name }}@if (email.company) { · {{ email.company }} } · {{ email.sendAt | date:'EEE d MMM, HH:mm' }}</span>
              <button mat-button type="button" (click)="cancel(email)" data-scheduled-email-cancel>{{ 'common.cancel' | translate }}</button>
            </li>
          }
        </ul>
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
    .own { margin: 4px 0 8px 32px; }
    .row { display: flex; justify-content: flex-end; margin-top: 8px; }
    .when { display: flex; flex-wrap: wrap; gap: 0 12px; margin-top: 12px; }
    .when mat-form-field { flex: 1 1 200px; }
    .waiting-title { margin: 16px 0 4px; font-weight: 500; }
    .waiting { list-style: none; margin: 0; padding: 0; }
    .campaigns { list-style: none; margin: 0 0 8px; padding: 0; }
    .campaigns li { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 2px 8px; padding: 4px 0; }
    .campaigns .results { color: var(--mat-sys-on-surface-variant); font-size: .875rem; }
    .waiting li { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 4px 8px; }
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
  private readonly ai = inject(AiService);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);

  recipients: AvailabilityRecipient[] | null = null;
  readonly chosen = new Set<number>();
  subject = '';
  body = '';
  dossier = true;
  sending = false;
  /** 0 to send now, else over how many working days. */
  days = 0;
  dailyMax = 15;
  readonly spreads = [1, 2, 3, 5];
  waiting: ScheduledEmail[] = [];
  /** The latest campaigns and what came of them. */
  campaigns: ProspectionCampaign[] = [];
  /** Messages written for one contact, by contact, in place of the shared one. */
  readonly own = new Map<number, string>();
  /** The contact whose message is open for editing. */
  editing: number | null = null;

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
    this.loadWaiting();
    this.loadCampaigns();
  }

  private loadCampaigns(): void {
    this.contacts.campaigns().subscribe({
      next: campaigns => {
        this.campaigns = Array.isArray(campaigns) ? campaigns : [];
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** The contacts listed today that the campaign did not write to. */
  newTargets(campaign: ProspectionCampaign): AvailabilityRecipient[] {
    const reached = new Set(campaign.contactIds ?? []);
    return (this.recipients ?? []).filter(recipient => !reached.has(recipient.id));
  }

  /**
   * Starts again from a campaign: its subject, and every contact listed today that it did not write to ticked, but for
   * those {@link SILENT_LIMIT} campaigns or more wrote to without an answer (left for the user to tick).
   */
  prospectAgain(campaign: ProspectionCampaign): void {
    this.chosen.clear();
    const targets = this.newTargets(campaign);
    targets.filter(recipient => (recipient.silentCampaigns ?? 0) < SILENT_LIMIT).forEach(recipient => this.chosen.add(recipient.id));
    this.subject = campaign.name;
    const left = targets.length - this.chosen.size;
    this.notifications.info(left ? 'availabilityEmails.againReadyLeftOut' : 'availabilityEmails.againReady', 6000,
      left ? { count: this.chosen.size, left } : { count: this.chosen.size });
  }

  /** A new campaign for this sending, named after its subject; none (the emails still go) when it cannot be started. */
  private campaign(): Observable<number | null> {
    return this.contacts.startCampaign(this.subject.trim()).pipe(map(campaign => campaign.id), catchError(() => of(null)));
  }

  private loadWaiting(): void {
    this.contacts.scheduledEmails().subscribe({
      next: waiting => {
        this.waiting = Array.isArray(waiting) ? waiting : [];
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** Cancels an email waiting to go. */
  cancel(email: ScheduledEmail): void {
    this.contacts.cancelScheduledEmail(email.id).subscribe({
      next: () => {
        this.waiting = this.waiting.filter(other => other !== email);
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('availabilityEmails.cancelFailed')
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
    if (this.days) {
      this.campaign().subscribe(campaignId => this.schedule(recipients, campaignId));
      return;
    }
    this.campaign().pipe(
      switchMap(campaignId => from(recipients).pipe(
      concatMap(recipient => this.contacts.sendEmail(recipient.id, {
        ...this.written(recipient), track: false, copy: false, language: this.language, dossier: this.dossier, campaignId
      }).pipe(map(() => ({ recipient, sent: true })), catchError(() => of({ recipient, sent: false })))),
      toArray()))
    ).subscribe(results => {
      this.sending = false;
      const sent = results.filter(result => result.sent);
      sent.forEach(result => {
        this.chosen.delete(result.recipient.id);
        this.own.delete(result.recipient.id);
      });
      const failed = results.filter(result => !result.sent).map(result => result.recipient.name);
      if (sent.length) {
        this.notifications.success('availabilityEmails.sent', 4000, { count: sent.length });
      }
      if (failed.length) {
        this.notifications.error('availabilityEmails.failed', 8000, { names: failed.join(', ') });
      }
      this.loadCampaigns();
      this.changes.markForCheck();
    });
  }

  /** Plans one email each over the working days chosen; nothing is planned when the server refuses. */
  private schedule(recipients: AvailabilityRecipient[], campaignId: number | null): void {
    const dailyMax = Math.min(50, Math.max(1, Math.round(Number(this.dailyMax) || 1)));
    this.contacts.scheduleEmails({
      emails: recipients.map(recipient => ({ contactId: recipient.id, ...this.written(recipient) })),
      dossier: this.dossier, language: this.language, days: this.days, dailyMax, campaignId
    }).subscribe({
      next: planned => {
        this.sending = false;
        recipients.forEach(recipient => {
          this.chosen.delete(recipient.id);
          this.own.delete(recipient.id);
        });
        this.notifications.success('availabilityEmails.scheduled', 6000, { count: planned.length });
        this.loadWaiting();
        this.loadCampaigns();
        this.changes.markForCheck();
      },
      error: () => {
        this.sending = false;
        this.notifications.error('availabilityEmails.scheduleFailed');
        this.changes.markForCheck();
      }
    });
  }

  /** Opens or closes the message of one contact, written from the shared one until changed. */
  edit(recipient: AvailabilityRecipient): void {
    this.editing = this.editing === recipient.id ? null : recipient.id;
  }

  /** The list shows the likeliest this many contacts first; **Show all** lists the others. */
  readonly cap = 20;
  showAll = false;

  shownRecipients(): AvailabilityRecipient[] {
    const all = this.recipients ?? [];
    return this.showAll ? all : all.slice(0, this.cap);
  }

  /** At most this many contacts get their own message written by AI at once. */
  readonly maxWritten = 20;
  writing = false;

  /**
   * Writes each ticked contact's own message with AI from the shared one: kept as their own message, to read with
   * **Edit** before sending; nothing is sent.
   */
  writeEach(): void {
    const ids = [...this.chosen];
    this.writing = true;
    this.ai.prospectionMessages(this.subject.trim(), this.body, ids, this.language).subscribe({
      next: messages => {
        this.writing = false;
        messages.forEach(message => this.own.set(message.contactId, message.body));
        this.notifications.success('availabilityEmails.writtenEach', 6000, { count: messages.length });
        this.changes.markForCheck();
      },
      error: () => {
        this.writing = false;
        this.notifications.error('availabilityEmails.writeEachError');
        this.changes.markForCheck();
      }
    });
  }

  ownBody(recipient: AvailabilityRecipient): string {
    return this.own.get(recipient.id) ?? this.personalise(this.body, recipient);
  }

  setOwn(recipient: AvailabilityRecipient, body: string): void {
    this.own.set(recipient.id, body);
  }

  resetOwn(recipient: AvailabilityRecipient): void {
    this.own.delete(recipient.id);
  }

  /** The subject and message for one contact: its own message if written, else the shared one with its details in place. */
  private written(recipient: AvailabilityRecipient): { subject: string; body: string } {
    const own = this.own.get(recipient.id);
    return {
      subject: this.personalise(this.subject.trim(), recipient),
      body: own?.trim() ? own : this.personalise(this.body, recipient)
    };
  }

  /** `{{firstName}}`, `{{company}}` and `{{mission}}` replaced by the contact's (the mission's sentence left out without one). */
  private personalise(text: string, recipient: AvailabilityRecipient): string {
    const mission = recipient.mission?.trim();
    const withMission = mission ? text.replaceAll('{{mission}}', mission)
      : text.split('\n').filter(line => !line.includes('{{mission}}')).join('\n');
    return withMission.replaceAll('{{firstName}}', recipient.firstName?.trim() || recipient.name).replaceAll('{{company}}', recipient.company);
  }
}
