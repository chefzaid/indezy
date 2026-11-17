import { AfterViewChecked, ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, ElementRef, EventEmitter, Input, OnChanges, Output,
  ViewChild, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription, catchError, filter, interval, of, throttleTime } from 'rxjs';

import { Conversation, MESSAGE_MAX, Message, ProjectDto, REACTIONS, Reaction } from '../../../models';
import { GroupDialogComponent } from './group-dialog.component';
import { ProjectService } from '../../../services/project/project.service';
import { AuthService } from '../../../services/auth/auth.service';
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { NetworkStreamService } from '../../../services/network/network-stream.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';
import { saveBlob } from '../../../shared/utils/download-file';
import { SwipeReplyDirective } from '../../../shared/directives/swipe-reply.directive';
import { ThreadDay, byDay, memberNames, reactionEmoji, seenMessageId } from './message-thread.utils';
import { FilePickerComponent } from '../files/file-picker.component';
import { NetworkFilesComponent } from '../files/network-files.component';

/** How often an open conversation asks for new messages, while the stream is closed. */
export const THREAD_REFRESH_MS = 10_000;
/** How often the others are told the reader is typing, and how long "is typing" shows after the last time told. */
export const TYPING_EVERY_MS = 4_000;
export const TYPING_SHOWN_MS = 6_000;
/** How many of the reader's opportunities the share list offers. */
const OPPORTUNITIES_SHOWN = 20;
/** How many members a group has at most. */
const GROUP_MAX = 20;


/**
 * A conversation: its messages as bubbles by day, the reader's on the right, then the field to write; a message is
 * replied to, reacted to, and, by its sender within 15 minutes, edited or deleted. New messages come every 10 seconds.
 */
@Component({
  selector: 'app-message-thread',
  imports: [MemberAvatarComponent, DatePipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatIconModule, MatMenuModule,
    MatTooltipModule, TranslateModule, SwipeReplyDirective, FilePickerComponent, NetworkFilesComponent],
  templateUrl: './message-thread.component.html',
  styleUrls: ['./message-thread.component.scss', './message-cards.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MessageThreadComponent implements OnChanges, AfterViewChecked {
  private readonly api = inject(NetworkMessagesService);
  private readonly notices = inject(NetworkNoticesService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly translate = inject(TranslateService);
  private readonly projects = inject(ProjectService);
  private readonly auth = inject(AuthService);
  private readonly confidentiality = inject(ConfidentialityGuardService);
  private readonly stream = inject(NetworkStreamService);
  private polling: Subscription | null = null;
  private typingTimer: ReturnType<typeof setTimeout> | null = null;
  /** Who is typing in the conversation, by their first name; none while nobody is. */
  typing: string | null = null;
  private scrollDown = false;

  @Input({ required: true }) conversationId!: number;
  /** The conversation as it now reads, for the list beside it. */
  @Output() readonly changed = new EventEmitter<Conversation>();
  /** The group's name being changed; none while not renaming. */
  renaming: FormControl<string> | null = null;
  @ViewChild('titleField') titleField?: ElementRef<HTMLInputElement>;
  /** The reader left this group. */
  @Output() readonly left = new EventEmitter<number>();
  @ViewChild('scroller') scroller?: ElementRef<HTMLElement>;
  @ViewChild('field') field?: ElementRef<HTMLTextAreaElement>;
  @ViewChild(FilePickerComponent) picker?: FilePickerComponent;

  readonly max = MESSAGE_MAX;
  readonly reactions = REACTIONS;
  /** The reader's opportunities to share; none loaded yet while null. */
  opportunities: ProjectDto[] | null = null;
  readonly draft = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(MESSAGE_MAX)] });
  conversation: Conversation | null = null;
  messages: Message[] = [];
  more = false;
  loading = true;
  missing = false;
  sending = false;
  replyTo: Message | null = null;
  editing: Message | null = null;

  constructor() {
    this.stream.events.pipe(filter(event => event.conversationId === this.conversationId), takeUntilDestroyed(this.destroyRef))
      .subscribe(event => {
        if (event.type === 'typing') {
          this.showTyping(event.name ?? '');
        } else {
          this.showTyping(null);
          this.load(false);
        }
      });
    this.draft.valueChanges.pipe(filter(text => text.trim() !== '' && this.editing === null), throttleTime(TYPING_EVERY_MS),
      takeUntilDestroyed(this.destroyRef)).subscribe(() => this.api.typing(this.conversationId).pipe(catchError(() => of(null))).subscribe());
    this.destroyRef.onDestroy(() => this.showTyping(null));
  }

  /** Shows who is typing for a few seconds, or stops showing it. */
  private showTyping(name: string | null): void {
    if (this.typingTimer !== null) {
      clearTimeout(this.typingTimer);
      this.typingTimer = null;
    }
    this.typing = name;
    if (name !== null) {
      this.typingTimer = setTimeout(() => this.showTyping(null), TYPING_SHOWN_MS);
    }
    this.changes.markForCheck();
  }

  /** The latest of the reader's messages the other member read, while both show read receipts. */
  get seenId(): number | null {
    return seenMessageId(this.messages, this.conversation?.seenAt);
  }

  ngOnChanges(): void {
    this.showTyping(null);
    this.conversation = null;
    this.messages = [];
    this.replyTo = null;
    this.editing = null;
    this.loading = true;
    this.missing = false;
    this.load(true);
    this.polling?.unsubscribe();
    this.polling = interval(THREAD_REFRESH_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (!document.hidden && !this.stream.connected) {
        this.load(false);
      }
    });
  }

  ngAfterViewChecked(): void {
    if (this.scrollDown && this.scroller) {
      this.scrollDown = false;
      this.scroller.nativeElement.scrollTop = this.scroller.nativeElement.scrollHeight;
    }
  }

  /** The latest messages: on opening, scrolled to the end; later, merged with those shown, read when new ones came. */
  load(opening: boolean): void {
    const id = this.conversationId;
    this.api.thread(id).subscribe({
      next: thread => {
        if (id !== this.conversationId) {
          return;
        }
        const known = new Set(this.messages.map(message => message.id));
        const arrived = thread.messages.some(message => !known.has(message.id));
        const latest = new Map(thread.messages.map(message => [message.id, message]));
        const older = this.messages.filter(message => message.id < (thread.messages[0]?.id ?? Infinity));
        this.messages = [...older.map(message => latest.get(message.id) ?? message), ...thread.messages];
        this.conversation = thread.conversation;
        if (opening) {
          this.more = thread.more;
        }
        this.loading = false;
        if (opening || (arrived && this.nearEnd())) {
          this.scrollDown = true;
        }
        if (opening || (arrived && thread.conversation.unread > 0)) {
          // After this check, so the list beside follows without changing what was just shown.
          setTimeout(() => this.markRead());
        }
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        if (error.status === 404) {
          this.missing = true;
          this.polling?.unsubscribe();
        } else if (opening) {
          this.notifications.error('network.messages.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  /** The messages before those shown. */
  loadOlder(): void {
    this.api.thread(this.conversationId, this.messages[0].id).subscribe({
      next: thread => {
        this.messages = [...thread.messages, ...this.messages];
        this.more = thread.more;
      },
      error: () => this.notifications.error('network.messages.loadError')
    });
  }

  /** The messages grouped by the day they were sent. */
  get days(): ThreadDay[] {
    return byDay(this.messages);
  }

  readonly emoji = reactionEmoji;

  /** Enter sends, Shift+Enter goes to the next line. */
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      this.submit();
    } else if (event.key === 'Escape' && (this.replyTo || this.editing)) {
      this.cancel();
    }
  }

  /** Sends the message, or saves the one being edited. */
  submit(): void {
    const text = this.draft.value.trim();
    const files = this.editing ? [] : this.picker?.refs() ?? [];
    if ((!text && !files.length) || this.draft.hasError('maxlength') || this.sending || !this.conversation ||
      (this.conversation.closed && !this.editing) || (!this.editing && this.picker && !this.picker.valid)) {
      return;
    }
    this.sending = true;
    if (!text) {
      this.deliver('');
      return;
    }
    // The workspace's client, ESN and contact names are pointed out before the message goes, as before a post.
    this.confidentiality.review(text).subscribe(choice => {
      if (choice?.action === 'publish') {
        this.deliver(choice.text);
        return;
      }
      this.sending = false;
      if (choice) {
        this.draft.setValue(choice.text);
        this.notifications.success('network.confidentiality.anonymized');
      }
    });
  }

  /** Sends the text checked, or saves the message being edited. */
  private deliver(text: string): void {
    const editing = this.editing;
    (editing ? this.api.edit(editing.id, text) : this.api.send(this.conversationId, text, this.replyTo?.id ?? null, null,
      this.picker?.refs() ?? [])).subscribe({
      next: message => {
        this.sending = false;
        this.draft.reset('');
        if (!editing) {
          this.picker?.clear();
        }
        this.replyTo = null;
        this.editing = null;
        this.messages = editing ? this.messages.map(item => item.id === message.id ? message : item) : [...this.messages, message];
        if (!editing) {
          this.scrollDown = true;
          this.changed.emit({ ...this.conversation!, last: { mine: true, text: message.text, deleted: false, sentAt: message.sentAt },
            lastMessageAt: message.sentAt, unread: 0 });
        }
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.sending = false;
        this.notifications.error(error.status === 409 ? 'network.messages.closedError' : error.status === 429
          ? 'network.messages.tooMany' : 'network.messages.sendError');
        if (error.status === 409) {
          this.load(false);
        }
      }
    });
  }

  reply(message: Message): void {
    this.editing = null;
    this.replyTo = message;
    this.field?.nativeElement.focus();
  }

  startEditing(message: Message): void {
    this.replyTo = null;
    this.editing = message;
    this.draft.setValue(message.text);
    this.field?.nativeElement.focus();
  }

  cancel(): void {
    if (this.editing) {
      this.draft.reset('');
    }
    this.replyTo = null;
    this.editing = null;
  }

  remove(message: Message): void {
    this.confirm.confirm({ messageKey: 'network.messages.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.api.remove(message.id).subscribe({
          next: deleted => this.messages = this.messages.map(item => item.id === deleted.id ? deleted : item),
          error: () => this.notifications.error('network.messages.changeError')
        });
      }
    });
  }

  /** Reacts to a message, or takes the reaction back when it is the same. */
  react(message: Message, reaction: Reaction): void {
    this.api.react(message.id, message.myReaction === reaction ? null : reaction).subscribe({
      next: updated => this.messages = this.messages.map(item => item.id === updated.id ? updated : item),
      error: () => this.notifications.error('network.messages.changeError')
    });
  }

  /** The reader's opportunities to share, loaded when the list opens, the latest first. */
  loadOpportunities(): void {
    if (this.opportunities !== null) {
      return;
    }
    const user = this.auth.getUser();
    this.projects.getByFreelanceId(user?.id ?? 0).subscribe({
      next: found => this.opportunities = [...found].filter(project => !project.archivedAt).reverse().slice(0, OPPORTUNITIES_SHOWN),
      error: () => {
        this.opportunities = [];
        this.notifications.error('network.messages.loadError');
      }
    });
  }

  /** Shares one of the reader's opportunities as a card, with what is written as its message. */
  share(project: ProjectDto): void {
    if (this.sending || !this.conversation || this.conversation.closed) {
      return;
    }
    this.sending = true;
    this.api.send(this.conversationId, this.draft.value.trim(), this.replyTo?.id ?? null, project.id ?? null).subscribe({
      next: message => {
        this.sending = false;
        this.draft.reset('');
        this.replyTo = null;
        this.messages = [...this.messages, message];
        this.scrollDown = true;
        this.changes.markForCheck();
      },
      error: () => {
        this.sending = false;
        this.notifications.error('network.messages.sendError');
      }
    });
  }

  /** Adds the opportunity a message shares to the reader's pipeline, the card then linking to it. */
  take(message: Message): void {
    this.api.take(message.id).subscribe({
      next: project => {
        this.messages = this.messages.map(item => item.id === message.id && item.card ? { ...item, card: { ...item.card,
          takenProjectId: project.id ?? null } } : item);
        this.notifications.success('network.pipeline.added');
      },
      error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'network.messages.cardAlready' : 'network.messages.cardError')
    });
  }

  /** Reports another member's message to the moderators, its text going with the report. */
  report(message: Message): void {
    ReportDialogComponent.open(this.dialog, { target: 'messages', id: message.id }).subscribe();
  }

  /** Downloads the whole conversation as a PDF, in the page's language. */
  downloadPdf(): void {
    const language = this.translate.currentLang === 'en' ? 'EN' : 'FR';
    this.api.pdf(this.conversationId, language).subscribe({
      next: pdf => saveBlob(pdf, `conversation-${this.conversation?.with?.handle ?? this.conversationId}.pdf`),
      error: () => this.notifications.error('network.messages.pdfError')
    });
  }

  /** Who the reader writes to: the other member's first name, or the group's name. */
  get label(): string {
    return this.conversation?.group?.title ?? this.conversation?.with?.firstName ?? this.translate.instant('network.messages.formerMember');
  }

  readonly memberNames = memberNames;

  /** Adds members to the reader's own group, among their followers. */
  addMembers(): void {
    const group = this.conversation!.group!;
    GroupDialogComponent.open(this.dialog, { title: group.title, present: group.members.map(member => member.handle),
      room: GROUP_MAX - group.size }).subscribe(choice => {
      if (choice) {
        this.api.addMembers(this.conversationId, choice.handles).subscribe({
          next: conversation => this.updatedGroup(conversation),
          error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'network.groups.refused' : 'network.groups.error')
        });
      }
    });
  }

  /** Starts renaming the reader's own group, in the header. */
  rename(): void {
    this.renaming = new FormControl(this.conversation!.group!.title, { nonNullable: true, validators: [Validators.required,
      Validators.maxLength(80)] });
    setTimeout(() => this.titleField?.nativeElement.focus());
  }

  /** Saves the group's new name; Escape or the cross keeps the old one. */
  saveTitle(): void {
    const title = this.renaming?.value.trim();
    if (!title || this.renaming?.invalid) {
      return;
    }
    this.api.renameGroup(this.conversationId, title).subscribe({
      next: conversation => {
        this.renaming = null;
        this.updatedGroup(conversation);
      },
      error: () => this.notifications.error('network.groups.error')
    });
  }

  /** Leaves the group once confirmed: the reader reads it no more. */
  leave(): void {
    this.confirm.confirm({ messageKey: 'network.groups.leaveConfirm', messageParams: { title: this.conversation!.group!.title },
      confirmKey: 'network.groups.leave', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.api.leaveGroup(this.conversationId).subscribe({
          next: () => {
            this.left.emit(this.conversationId);
            this.notices.refreshMessages();
          },
          error: () => this.notifications.error('network.groups.error')
        });
      }
    });
  }

  private updatedGroup(conversation: Conversation): void {
    this.conversation = conversation;
    this.changed.emit(conversation);
    this.changes.markForCheck();
  }

  /** Mutes or archives the conversation, for the reader alone. */
  set(settings: { muted?: boolean; archived?: boolean }): void {
    this.api.settings(this.conversationId, settings).subscribe({
      next: conversation => {
        this.conversation = conversation;
        this.changed.emit(conversation);
        this.notices.refreshMessages();
      },
      error: () => this.notifications.error('network.messages.changeError')
    });
  }

  private markRead(): void {
    this.api.read(this.conversationId).subscribe({
      next: conversation => {
        this.changed.emit(conversation);
        this.notices.refreshMessages();
      },
      error: () => undefined
    });
  }

  private nearEnd(): boolean {
    const element = this.scroller?.nativeElement;
    return !element || element.scrollHeight - element.scrollTop - element.clientHeight < 120;
  }
}
