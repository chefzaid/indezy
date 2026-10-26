import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Inject, LOCALE_ID, OnInit } from '@angular/core';
import { formatDate, formatNumber } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { catchError, forkJoin, map, Observable, of, OperatorFunction } from 'rxjs';

import { ContactDto, EmailTemplate, ProjectDto } from '../../../models';
import { UserProfile } from '../../../models/user-management.models';
import { ContactService } from '../../../services/contact/contact.service';
import { EmailTemplateService } from '../../../services/email-template/email-template.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { UserManagementService } from '../../../services/user-management/user-management.service';
import { EMAIL_VARIABLES, EmailValues, fillTemplate, mailtoLink } from '../../../shared/utils/email-template';

/** The templates always offered, in the interface language (translation keys under emails.builtIn). */
export const BUILT_IN_EMAILS = ['followUp', 'rateProposal', 'availability'];

/** A template to pick: a built-in one by its key, or one of the workspace's own. */
interface TemplateChoice {
  key: string;
  name: string;
  subject: string;
  body: string;
}

/**
 * Writes an email about an opportunity from a template: the recipient among the contacts of its client and ESN, the
 * variables filled from the opportunity, the contact and the profile, then the message opened in the mail app or copied.
 */
@Component({
  selector: 'app-project-email-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule,
    TranslateModule],
  templateUrl: './project-email-dialog.component.html',
  styles: [`
    .email-form { display: flex; flex-direction: column; min-width: min(560px, 85vw); padding-top: 4px; }
    .missing { margin: 0 0 8px; color: var(--app-warning, #b26a00); font-size: .875rem; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProjectEmailDialogComponent implements OnInit {
  readonly form: FormGroup;
  templates: TemplateChoice[] = [];
  contacts: ContactDto[] = [];
  private profile: UserProfile | null = null;

  constructor(
    fb: FormBuilder,
    @Inject(MAT_DIALOG_DATA) readonly project: ProjectDto,
    @Inject(LOCALE_ID) private readonly locale: string,
    private readonly contactService: ContactService,
    private readonly templateService: EmailTemplateService,
    private readonly userService: UserManagementService,
    private readonly notificationService: NotificationService,
    private readonly translate: TranslateService,
    private readonly changes: ChangeDetectorRef
  ) {
    this.form = fb.group({ template: [BUILT_IN_EMAILS[0]], recipient: [null], subject: [''], body: [''] });
  }

  ngOnInit(): void {
    let failed = false;
    const recover = <T>(fallback: T): OperatorFunction<T, T> => catchError(() => {
      failed = true;
      return of(fallback);
    });
    const clientIds = [this.project.middlemanId, this.project.clientId].filter((id): id is number => !!id);
    const contacts: Observable<ContactDto[]> = clientIds.length ?
      forkJoin(clientIds.map(id => this.contactService.getByClientId(id).pipe(recover<ContactDto[]>([])))).pipe(map(lists => lists.flat())) :
      of([]);
    forkJoin({
      contacts,
      own: this.templateService.list().pipe(recover<EmailTemplate[]>([])),
      profile: this.userService.getUserProfile().pipe(map((profile): UserProfile | null => profile), recover<UserProfile | null>(null))
    }).subscribe(({ contacts, own, profile }) => {
      this.contacts = contacts.filter(contact => !!contact.email);
      this.profile = profile;
      this.templates = [
        ...BUILT_IN_EMAILS.map(key => ({
          key,
          name: this.translate.instant(`emails.builtIn.${key}.name`),
          subject: this.translate.instant(`emails.builtIn.${key}.subject`),
          body: this.translate.instant(`emails.builtIn.${key}.body`)
        })),
        ...own.map(template => ({ key: `own-${template.id}`, name: template.name, subject: template.subject, body: template.body }))
      ];
      this.form.patchValue({ recipient: this.contacts[0]?.id ?? null }, { emitEvent: false });
      this.fill();
      if (failed) {
        this.notificationService.error('emails.loadError');
      }
      this.changes.markForCheck();
    });
  }

  /** Writes the chosen template for the chosen recipient, over what was typed. */
  fill(): void {
    const template = this.templates.find(choice => choice.key === this.form.value.template);
    if (!template) {
      return;
    }
    const values = this.values();
    this.form.patchValue({ subject: fillTemplate(template.subject, values), body: fillTemplate(template.body, values) });
  }

  /** Whether the message still names a variable without a value. */
  get missing(): boolean {
    const text = `${this.form.value.subject} ${this.form.value.body}`;
    return EMAIL_VARIABLES.some(variable => new RegExp(`\\{\\{\\s*${variable}\\s*\\}\\}`).test(text));
  }

  get mailto(): string {
    return mailtoLink(this.recipient()?.email, this.form.value.subject, this.form.value.body);
  }

  copy(): void {
    globalThis.navigator.clipboard.writeText(`${this.form.value.subject}\n\n${this.form.value.body}`).then(
      () => this.notificationService.success('emails.copied'),
      () => this.notificationService.error('emails.copyError'));
  }

  private recipient(): ContactDto | undefined {
    return this.contacts.find(contact => contact.id === this.form.value.recipient);
  }

  private values(): EmailValues {
    const contact = this.recipient();
    const profile = this.profile;
    return {
      firstName: contact?.firstName,
      lastName: contact?.lastName,
      company: contact?.clientName ?? this.project.middlemanName ?? this.project.clientName,
      role: this.project.role,
      rate: this.project.dailyRate ? `${formatNumber(this.project.dailyRate, this.locale, '1.0-0')} €` : undefined,
      availability: profile?.availabilityDate ? formatDate(profile.availabilityDate, 'longDate', this.locale) : undefined,
      myName: profile ? `${profile.firstName ?? ''} ${profile.lastName ?? ''}` : undefined,
      myPhone: profile?.phone
    };
  }
}
