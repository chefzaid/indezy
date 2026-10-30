import { ChangeDetectionStrategy, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { forkJoin, of } from 'rxjs';

import { ContactDto, ContactInteraction, INTERACTION_CHANNELS, InteractionChannel, InteractionDirection, contactName } from '../../../models/contact.models';
import { ProjectDto, ProjectNote } from '../../../models';
import { ContactService } from '../../../services/contact/contact.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ProjectService } from '../../../services/project/project.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MarkdownService } from '../../../shared/services/markdown.service';
import { CHANNEL_ICONS, SUMMARY_MAX } from '../../contacts/contact-interactions/contact-interactions.component';
import { toIsoDate } from '../../../shared/locale/app-locale';
import { NOTE_TEMPLATES } from '../project-detail/note-templates';

/** A line of the journal: a note written on the opportunity, or an exchange about it with a contact. */
export type JournalEntry = { kind: 'note'; at: string; note: ProjectNote } | { kind: 'exchange'; at: string; exchange: ContactInteraction };

/**
 * The opportunity's history in one place: the notes written on it (in Markdown, from templates) and the exchanges with
 * contacts recorded about it (emails, calls, LinkedIn messages, meetings), newest first. An exchange is recorded here
 * with a contact of the client or its ESN, and changed or deleted on the contact's page.
 */
@Component({
  selector: 'app-project-journal',
  imports: [DatePipe, FormsModule, ReactiveFormsModule, RouterModule, MatButtonModule, MatButtonToggleModule, MatCardModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './project-journal.component.html',
  styleUrls: ['./project-journal.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProjectJournalComponent implements OnChanges {
  @Input({ required: true }) project!: ProjectDto;

  private readonly projectService = inject(ProjectService);
  private readonly contactService = inject(ContactService);
  private readonly notificationService = inject(NotificationService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly markdownService = inject(MarkdownService);
  private readonly translate = inject(TranslateService);

  readonly noteTemplates = NOTE_TEMPLATES;
  readonly channels = INTERACTION_CHANNELS;
  readonly icons = CHANNEL_ICONS;
  readonly summaryMax = SUMMARY_MAX;
  readonly exchangeForm = inject(FormBuilder).nonNullable.group({
    contactId: [null as number | null, Validators.required],
    occurredOn: [toIsoDate(new Date()), Validators.required],
    channel: ['EMAIL' as InteractionChannel],
    direction: ['OUTGOING' as InteractionDirection],
    answered: [null as boolean | null],
    summary: ['', Validators.maxLength(SUMMARY_MAX)]
  });

  notes: ProjectNote[] = [];
  exchanges: ContactInteraction[] = [];
  /** Notes and exchanges, newest first; an exchange, dated by its day, comes before the notes of that day. */
  entries: JournalEntry[] = [];
  /** The contacts of the client and its ESN, whom an exchange about the opportunity is with. */
  contacts: ContactDto[] = [];
  newNoteContent = '';
  isSavingNote = false;
  selectedNoteTemplate = '';
  recording = false;
  isSavingExchange = false;
  today = toIsoDate(new Date());

  ngOnChanges(): void {
    this.loadNotes();
    this.loadExchanges();
    const companies = [this.project.clientId, this.project.middlemanId].filter((id): id is number => !!id);
    forkJoin(companies.length ? companies.map(id => this.contactService.getByClientId(id)) : [of([] as ContactDto[])]).subscribe({
      next: lists => this.contacts = lists.flat().sort((a, b) => this.nameOf(a).localeCompare(this.nameOf(b))),
      error: () => this.contacts = []
    });
  }


  readonly nameOf = contactName;

  /** Renders note content written in Markdown as safe, bindable HTML. */
  renderMarkdown(text: string): SafeHtml {
    return this.markdownService.render(text);
  }

  /** Inserts the selected note template, after any text already typed, then clears the template selection. */
  applyNoteTemplate(templateId: string): void {
    if (!templateId) { return; }
    const body = this.translate.instant(`projects.notes.templates.${templateId}.body`) as string;
    this.newNoteContent = this.newNoteContent.trim() ? `${this.newNoteContent.trimEnd()}\n\n${body}` : body;
    this.selectedNoteTemplate = '';
  }

  addNote(): void {
    const content = this.newNoteContent.trim();
    if (!content || this.isSavingNote) { return; }
    this.isSavingNote = true;
    this.projectService.addProjectNote(this.project.id!, content).subscribe({
      next: note => {
        this.notes = [note, ...this.notes];
        this.refreshEntries();
        this.newNoteContent = '';
        this.isSavingNote = false;
      },
      error: () => {
        this.notificationService.error('projects.notes.saveError');
        this.isSavingNote = false;
      }
    });
  }

  deleteNote(noteId: number): void {
    const projectId = this.project.id!;
    this.confirmDialog.confirm({ messageKey: 'projects.notes.deleteConfirm', danger: true }).subscribe(confirmed => {
      if (!confirmed) { return; }
      this.projectService.deleteProjectNote(projectId, noteId).subscribe({
        next: () => {
          this.notes = this.notes.filter(note => note.id !== noteId);
          this.refreshEntries();
        },
        error: () => this.notificationService.error('projects.notes.deleteError')
      });
    });
  }

  startRecording(): void {
    this.today = toIsoDate(new Date());
    this.exchangeForm.reset({ contactId: this.contacts.length === 1 ? this.contacts[0].id! : null, occurredOn: this.today,
      channel: 'EMAIL', direction: 'OUTGOING', answered: null, summary: '' });
    this.recording = true;
  }

  recordExchange(): void {
    const { contactId, occurredOn, channel, direction, answered, summary } = this.exchangeForm.getRawValue();
    if (contactId === null || this.exchangeForm.invalid || this.isSavingExchange) {
      this.exchangeForm.markAllAsTouched();
      return;
    }
    this.isSavingExchange = true;
    this.contactService.addInteraction(contactId, {
      occurredOn, channel, direction, replied: direction === 'OUTGOING' ? answered : null, summary: summary.trim() || null,
      projectId: this.project.id
    }).subscribe({
      next: () => {
        this.isSavingExchange = false;
        this.recording = false;
        this.loadExchanges();
      },
      error: () => {
        this.isSavingExchange = false;
        this.notificationService.error('contacts.interactions.saveError');
      }
    });
  }

  private loadNotes(): void {
    this.projectService.getProjectNotes(this.project.id!).subscribe({
      next: notes => {
        this.notes = notes;
        this.refreshEntries();
      },
      error: () => this.notificationService.error('projects.notes.loadError')
    });
  }

  private loadExchanges(): void {
    this.contactService.getProjectInteractions(this.project.id!).subscribe({
      next: exchanges => {
        this.exchanges = exchanges;
        this.refreshEntries();
      },
      error: () => this.notificationService.error('projects.journal.exchangesError')
    });
  }

  private refreshEntries(): void {
    this.entries = [
      ...this.notes.map(note => ({ kind: 'note' as const, at: note.createdAt, note })),
      ...this.exchanges.map(exchange => ({ kind: 'exchange' as const, at: `${exchange.occurredOn}T23:59:59`, exchange }))
    ].sort((a, b) => b.at.localeCompare(a.at));
  }
}
