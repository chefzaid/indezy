import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, debounceTime, distinctUntilChanged, filter, interval, of, switchMap } from 'rxjs';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { Conversation, FoundMessage } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkStreamService } from '../../../services/network/network-stream.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { TimeAgoPipe } from '../time-ago/time-ago.pipe';
import { MessageThreadComponent } from './message-thread.component';
import { GroupDialogComponent } from './group-dialog.component';
import { NetworkNavComponent } from '../nav/network-nav.component';

/** How often the list of conversations is read again. */
export const CONVERSATIONS_REFRESH_MS = 30_000;
/** How long the list waits for a burst of events to end before reading itself again. */
export const STREAM_SETTLE_MS = 300;
/** How long the search waits for the typing to pause. */
export const SEARCH_DELAY_MS = 300;
/** How many members a new group takes besides its creator. */
const GROUP_ROOM = 19;

/**
 * Messages (`/network/messages`, `/network/messages/<id>`): the reader's conversations, the latest first, beside the one
 * open; on a phone, the list or the conversation.
 */
@Component({
  selector: 'app-messages-page',
  imports: [NetworkNavComponent, MatButtonModule, MatTooltipModule, ReactiveFormsModule, MemberAvatarComponent, MessageThreadComponent, TimeAgoPipe, RouterLink, MatIconModule, TranslateModule],
  templateUrl: './messages-page.component.html',
  styleUrls: ['./messages-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MessagesPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(NetworkMessagesService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly stream = inject(NetworkStreamService);

  conversations: Conversation[] = [];
  /** The list shown: the inbox, the message requests waiting for the reader's answer, or the archived conversations. */
  box: 'inbox' | 'requests' | 'archived' = 'inbox';
  /** How many message requests wait for the reader's answer. */
  requestCount = 0;
  loading = true;
  notMember = false;
  /** The conversation open beside the list. */
  openId: number | null = null;
  readonly query = new FormControl('', { nonNullable: true });
  /** The messages found, while a search of two characters or more is typed. */
  found: FoundMessage[] | null = null;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = params.get('id');
      this.openId = id ? Number(id) : null;
      this.changes.markForCheck();
    });
    this.load();
    this.query.valueChanges.pipe(debounceTime(SEARCH_DELAY_MS), distinctUntilChanged(), switchMap(typed => typed.trim().length < 2 ? of(null)
      : this.api.search(typed.trim()).pipe(catchError(() => {
        this.notifications.error('network.messages.loadError');
        return of([]);
      }))), takeUntilDestroyed(this.destroyRef)).subscribe(found => {
      this.found = found;
      this.changes.markForCheck();
    });
    // The list follows the stream; it polls only while the stream is closed.
    this.stream.events.pipe(filter(event => event.type === 'message' || event.type === 'read'), debounceTime(STREAM_SETTLE_MS),
      takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    interval(CONVERSATIONS_REFRESH_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (!document.hidden && !this.stream.connected) {
        this.load();
      }
    });
  }

  get archived(): boolean {
    return this.box === 'archived';
  }

  load(): void {
    this.api.requests().subscribe({
      next: requests => {
        this.requestCount = requests.length;
        if (this.box === 'requests') {
          this.conversations = requests;
          this.loading = false;
        }
        this.changes.markForCheck();
      },
      error: () => undefined
    });
    if (this.box === 'requests') {
      return;
    }
    this.api.conversations(this.archived).subscribe({
      next: conversations => {
        this.conversations = conversations;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.notMember = error.status === 409;
        if (!this.notMember) {
          this.notifications.error('network.messages.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  /** The reader left a group, or declined a message request: it leaves the list, and the page goes back to it. */
  left(id: number): void {
    if (this.box === 'requests' && this.conversations.some(item => item.id === id)) {
      this.requestCount = Math.max(0, this.requestCount - 1);
    }
    this.conversations = this.conversations.filter(item => item.id !== id);
    void this.router.navigate(['/network/messages']);
  }

  /** Names a group and picks its members among the reader's followers, then opens it. */
  /** Writes to one member the reader follows or is followed by: their conversation opens, a new one if none. */
  newMessage(): void {
    GroupDialogComponent.open(this.dialog, { title: null, present: [], room: 1, direct: true }).subscribe(choice => {
      const handle = choice?.handles[0];
      if (!handle) {
        return;
      }
      this.api.open(handle).subscribe({
        next: conversation => {
          this.updated(conversation);
          void this.router.navigate(['/network/messages', conversation.id]);
        },
        error: () => this.notifications.error('network.messages.openError')
      });
    });
  }

  newGroup(): void {
    GroupDialogComponent.open(this.dialog, { title: null, present: [], room: GROUP_ROOM }).subscribe(choice => {
      if (!choice) {
        return;
      }
      this.api.createGroup(choice.title, choice.handles).subscribe({
        next: group => {
          this.updated(group);
          void this.router.navigate(['/network/messages', group.id]);
        },
        error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'network.groups.refused' : 'network.groups.error')
      });
    });
  }

  /** The inbox, or the archived conversations. */
  showArchived(archived: boolean): void {
    this.show(archived ? 'archived' : 'inbox');
  }

  /** The inbox, the message requests, or the archived conversations. */
  show(box: 'inbox' | 'requests' | 'archived'): void {
    if (box !== this.box) {
      this.box = box;
      this.loading = true;
      this.load();
    }
  }

  /** The open conversation changed: its row follows, moved to the top when a message went, gone when it left this list. */
  updated(conversation: Conversation): void {
    const others = this.conversations.filter(item => item.id !== conversation.id);
    const belongs = this.box === 'requests' ? conversation.closed === 'REQUEST_RECEIVED' :
      conversation.archived === this.archived && conversation.closed !== 'REQUEST_RECEIVED';
    if (this.box === 'requests' && !belongs) {
      this.requestCount = Math.max(0, this.requestCount - 1);
    }
    const sent = this.conversations.find(item => item.id === conversation.id)?.lastMessageAt !== conversation.lastMessageAt;
    if (!belongs) {
      this.conversations = others;
    } else if (sent || !this.conversations.some(item => item.id === conversation.id)) {
      this.conversations = [conversation, ...others];
    } else {
      this.conversations = this.conversations.map(item => item.id === conversation.id ? conversation : item);
    }
  }
}
