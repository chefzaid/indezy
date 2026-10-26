import { ChangeDetectionStrategy, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import {
  ContactInteraction, ContactInteractions, INTERACTION_CHANNELS, InteractionChannel, InteractionDirection
} from '../../../models/contact.models';
import { ProjectDto } from '../../../models/project.models';
import { AuthService } from '../../../services/auth/auth.service';
import { ContactService } from '../../../services/contact/contact.service';
import { ProjectService } from '../../../services/project/project.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

export const SUMMARY_MAX = 500;
export const CHANNEL_ICONS: Record<InteractionChannel, string> = {
  EMAIL: 'email',
  PHONE: 'call',
  LINKEDIN: 'work',
  MEETING: 'groups',
  OTHER: 'chat'
};
type Answer = 'WAITING' | 'ANSWERED' | 'UNANSWERED';

/** The local calendar day of {@code date}, as yyyy-MM-dd. */
export function localDay(date: Date = new Date()): string {
  const pad = (value: number): string => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/**
 * The exchanges kept with a contact (messages, calls, meetings), newest first, with when it was last contacted, how
 * often it answers and the channel it answers on most. Exchanges are recorded, changed and deleted here.
 */
@Component({
  selector: 'app-contact-interactions',
  imports: [DatePipe, RouterModule, ReactiveFormsModule, MatButtonModule, MatButtonToggleModule, MatCardModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './contact-interactions.component.html',
  styleUrls: ['./contact-interactions.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ContactInteractionsComponent implements OnChanges {
  @Input({ required: true }) contactId!: number;

  private readonly contactService = inject(ContactService);
  private readonly notificationService = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly projectService = inject(ProjectService);
  private readonly authService = inject(AuthService);

  readonly channels = INTERACTION_CHANNELS;
  readonly icons = CHANNEL_ICONS;
  readonly summaryMax = SUMMARY_MAX;
  readonly form = inject(FormBuilder).nonNullable.group({
    occurredOn: [localDay(), Validators.required],
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
  today = localDay();

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
    this.today = localDay();
    this.editingId = null;
    this.form.reset({ occurredOn: this.today, channel: 'EMAIL', direction: 'OUTGOING', answer: 'WAITING', summary: '', projectId: null });
    this.formOpen = true;
  }

  startEdit(interaction: ContactInteraction): void {
    this.today = localDay();
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

  private load(): void {
    this.contactService.getInteractions(this.contactId).subscribe({
      next: history => this.history = history,
      error: () => this.notificationService.error('contacts.interactions.loadError')
    });
  }
}
