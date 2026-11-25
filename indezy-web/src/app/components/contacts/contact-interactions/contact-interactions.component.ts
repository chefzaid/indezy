import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnChanges, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import {
  ContactInteraction, ContactInteractions, INTERACTION_CHANNELS, InteractionChannel, InteractionDirection, ReplyKind
} from '../../../models/contact.models';
import { ProjectDto } from '../../../models/project.models';
import { AuthService } from '../../../services/auth/auth.service';
import { ContactService } from '../../../services/contact/contact.service';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { toIsoDate } from '../../../shared/locale/app-locale';

export const SUMMARY_MAX = 500;
export const CHANNEL_ICONS: Record<InteractionChannel, string> = {
  EMAIL: 'email',
  PHONE: 'call',
  LINKEDIN: 'work',
  MEETING: 'groups',
  OTHER: 'chat'
};
type Answer = 'WAITING' | 'ANSWERED' | 'UNANSWERED';

/**
 * The exchanges kept with a contact (messages, calls, meetings), newest first, with when it was last contacted, how
 * often it answers and the channel it answers on most. Exchanges are recorded, changed and deleted here.
 */
@Component({
  selector: 'app-contact-interactions',
  imports: [DatePipe, RouterModule, ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatMenuModule, MatSelectModule, TranslateModule],
  templateUrl: './contact-interactions.component.html',
  styleUrls: ['./contact-interactions.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ContactInteractionsComponent implements OnChanges {
  @Input({ required: true }) contactId!: number;
  /** Whether the contact is now not to be contacted, once an answer's sort changed it. */
  @Output() readonly doNotContactChanged = new EventEmitter<boolean>();

  private readonly contactService = inject(ContactService);
  private readonly notificationService = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly projectService = inject(ProjectService);
  private readonly authService = inject(AuthService);

  readonly channels = INTERACTION_CHANNELS;
  readonly icons = CHANNEL_ICONS;
  readonly replyKinds: ReplyKind[] = ['INTERESTED', 'NOT_NOW', 'NO_NEED', 'DO_NOT_CONTACT', 'OTHER'];
  readonly summaryMax = SUMMARY_MAX;
  readonly form = inject(FormBuilder).nonNullable.group({
    occurredOn: [toIsoDate(new Date()), Validators.required],
    channel: ['EMAIL' as InteractionChannel, Validators.required],
    direction: ['OUTGOING' as InteractionDirection, Validators.required],
    answer: ['WAITING' as Answer],
    summary: ['', Validators.maxLength(SUMMARY_MAX)],
    projectId: [null as number | null]
  });

  /** The workspace's opportunities in the pipeline, which an exchange may be about, by role. */
  projects: ProjectDto[] = [];

  history?: ContactInteractions;
  formOpen = false;
  editingId: number | null = null;
  saving = false;
  today = toIsoDate(new Date());

  ngOnChanges(): void {
    this.load();
    const freelanceId = this.authService.getUser()?.id;
    if (freelanceId && !this.projects.length) {
      this.projectService.getByFreelanceId(freelanceId).subscribe({
        next: projects => this.projects = projects.filter(project => !project.archivedAt)
          .sort((a, b) => a.role.localeCompare(b.role)),
        // The opportunity is optional: exchanges are recorded without one.
        error: () => this.projects = []
      });
    }
  }

  get outgoing(): boolean {
    return this.form.controls.direction.value === 'OUTGOING';
  }

  startAdd(): void {
    this.today = toIsoDate(new Date());
    this.editingId = null;
    this.form.reset({ occurredOn: this.today, channel: 'EMAIL', direction: 'OUTGOING', answer: 'WAITING', summary: '', projectId: null });
    this.formOpen = true;
  }

  startEdit(interaction: ContactInteraction): void {
    this.today = toIsoDate(new Date());
    this.editingId = interaction.id ?? null;
    this.form.reset({
      occurredOn: interaction.occurredOn,
      channel: interaction.channel,
      direction: interaction.direction,
      answer: interaction.replied === true ? 'ANSWERED' : interaction.replied === false ? 'UNANSWERED' : 'WAITING',
      summary: interaction.summary ?? '',
      projectId: interaction.projectId ?? null
    });
    this.formOpen = true;
  }

  cancel(): void {
    this.formOpen = false;
    this.editingId = null;
  }

  save(): void {
    if (this.form.invalid || this.saving) {
      this.form.markAllAsTouched();
      return;
    }
    const { occurredOn, channel, direction, answer, summary, projectId } = this.form.getRawValue();
    const interaction: ContactInteraction = {
      occurredOn, channel, direction,
      replied: direction === 'OUTGOING' && answer !== 'WAITING' ? answer === 'ANSWERED' : null,
      summary: summary.trim() || null,
      projectId
    };
    const request = this.editingId === null
      ? this.contactService.addInteraction(this.contactId, interaction)
      : this.contactService.updateInteraction(this.contactId, this.editingId, interaction);
    this.saving = true;
    request.subscribe({
      next: () => {
        this.saving = false;
        this.cancel();
        this.notificationService.success('contacts.interactions.saved');
        this.load();
      },
      error: () => {
        this.saving = false;
        this.notificationService.error('contacts.interactions.saveError');
      }
    });
  }

  remove(interaction: ContactInteraction): void {
    this.confirmDialog.confirm({ messageKey: 'contacts.interactions.confirmDelete', danger: true }).subscribe(confirmed => {
      if (!confirmed || interaction.id === undefined) {
        return;
      }
      this.contactService.deleteInteraction(this.contactId, interaction.id).subscribe({
        next: () => {
          if (this.editingId === interaction.id) {
            this.cancel();
          }
          this.load();
        },
        error: () => this.notificationService.error('contacts.interactions.deleteError')
      });
    });
  }

  /**
   * Sorts an answer received as the freelancer says, the AI having maybe got it wrong: a "not now" one written back to
   * three months later unless a day is given; "do not contact" marks the contact so, and sorting it otherwise unmarks it.
   */
  sortAnswer(interaction: ContactInteraction, kind: ReplyKind, writeBackOn: string | null = null): void {
    if (interaction.id === undefined) {
      return;
    }
    const before = interaction.replyKind;
    this.contactService.sortAnswer(this.contactId, interaction.id, kind, writeBackOn).subscribe({
      next: sorted => {
        if (this.history) {
          this.history = { ...this.history, interactions: this.history.interactions.map(kept => kept.id === sorted.id ? sorted : kept) };
        }
        if (kind === 'DO_NOT_CONTACT' || before === 'DO_NOT_CONTACT') {
          this.doNotContactChanged.emit(kind === 'DO_NOT_CONTACT');
        }
        this.notificationService.success('contacts.interactions.sorted');
      },
      error: () => this.notificationService.error('contacts.interactions.sortError')
    });
  }

  /**
   * The day to write back to a "not now" answer, as picked: from today on, which also skips the years a date field
   * passes through while one is typed (0002, 0020, 0202...).
   */
  writeBack(interaction: ContactInteraction, event: Event): void {
    const day = (event.target as HTMLInputElement).value;
    if (day && day >= toIsoDate(new Date())) {
      this.sortAnswer(interaction, 'NOT_NOW', day);
    }
  }

  private load(): void {
    this.contactService.getInteractions(this.contactId).subscribe({
      next: history => this.history = history,
      error: () => this.notificationService.error('contacts.interactions.loadError')
    });
  }
}
