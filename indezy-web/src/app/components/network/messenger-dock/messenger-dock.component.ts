import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, HostListener, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { debounceTime, filter, interval } from 'rxjs';

import { Conversation } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NetworkStreamService } from '../../../services/network/network-stream.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { MessageThreadComponent } from '../messages/message-thread.component';
import { TimeAgoPipe } from '../time-ago/time-ago.pipe';

/** How often the corner reads the conversations again, for who is online and, while the stream is closed, new messages. */
export const DOCK_REFRESH_MS = 30_000;
/** How long the corner waits for a burst of events to end before reading itself again. */
export const DOCK_SETTLE_MS = 300;
/** How wide a chat window is, with the gap beside it; the bar takes as much. */
export const DOCK_WINDOW_PX = 336;
/** How many chat windows stay open at most, however wide the screen. */
export const DOCK_MAX_WINDOWS = 3;

/** A conversation open as a chat window, folded to its head or not. */
interface ChatWindow {
  id: number;
  folded: boolean;
}

/**
 * The messenger corner, as on LinkedIn or Facebook: a bar at the bottom right of every page with the unread count,
 * opening the list of conversations, each with whether the member is online; a conversation opens as a small chat window
 * beside it (a few at once, the oldest closing first), to read and answer without leaving the page. Not on the Messages
 * page itself, nor for a visitor who has not joined the network.
 */
@Component({
  selector: 'app-messenger-dock',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule, MemberAvatarComponent, MessageThreadComponent, TimeAgoPipe],
  templateUrl: './messenger-dock.component.html',
  styleUrls: ['./messenger-dock.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MessengerDockComponent implements OnInit {
  private readonly api = inject(NetworkMessagesService);
  private readonly stream = inject(NetworkStreamService);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  conversations: Conversation[] = [];
  /** The visitor has not joined the network: no corner. */
  notMember = false;
  /** The conversations were read once: until then (the server out of reach, say), no corner. */
  loaded = false;
  /** The list of conversations is unfolded. */
  listOpen = false;
  windows: ChatWindow[] = [];
  /** On the Messages page, which shows the conversations already. */
  onMessagesPage = false;

  ngOnInit(): void {
    this.onMessagesPage = this.router.url.startsWith('/network/messages');
    this.router.events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef)).subscribe(event => {
      this.onMessagesPage = (event as NavigationEnd).urlAfterRedirects.startsWith('/network/messages');
      this.changes.markForCheck();
    });
    this.load();
    this.stream.events.pipe(filter(event => event.type === 'message' || event.type === 'read'), debounceTime(DOCK_SETTLE_MS),
      takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    // Who is online changes without an event: the list is read again while the page is shown.
    interval(DOCK_REFRESH_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (!document.hidden && !this.notMember) {
        this.load();
      }
    });
  }

  get shown(): boolean {
    return this.loaded && !this.notMember && !this.onMessagesPage;
  }

  /** The unread conversations, the muted aside, as the menu's count says. */
  get unread(): number {
    return this.conversations.filter(conversation => !conversation.muted && conversation.unread > 0).length;
  }

  load(): void {
    this.api.conversations(false, true).subscribe({
      next: conversations => {
        this.conversations = conversations;
        this.loaded = true;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.notMember = error.status === 409;
        this.changes.markForCheck();
      }
    });
  }

  toggleList(): void {
    this.listOpen = !this.listOpen;
  }

  /** Opens the conversation as a chat window, unfolded, the oldest window closing when there is no room left. */
  openChat(id: number): void {
    const open = this.windows.find(window => window.id === id);
    if (open) {
      open.folded = false;
    } else {
      this.windows = [...this.windows, { id, folded: false }].slice(-this.room());
    }
    if (this.narrow()) {
      this.listOpen = false;
    }
  }

  fold(chat: ChatWindow): void {
    chat.folded = !chat.folded;
  }

  close(id: number): void {
    this.windows = this.windows.filter(window => window.id !== id);
  }

  /** The conversation changed in its window (a message went, it was read): its row follows. */
  changed(conversation: Conversation): void {
    const others = this.conversations.filter(item => item.id !== conversation.id);
    this.conversations = [conversation, ...others].sort((a, b) => (b.lastMessageAt ?? '').localeCompare(a.lastMessageAt ?? ''));
  }

  /** The conversation was left (a group): its window closes and its row goes. */
  left(id: number): void {
    this.close(id);
    this.conversations = this.conversations.filter(item => item.id !== id);
  }

  conversation(id: number): Conversation | undefined {
    return this.conversations.find(item => item.id === id);
  }

  title(conversation: Conversation | undefined): string {
    if (!conversation) {
      return '';
    }
    return conversation.group?.title ?? (conversation.with ? `${conversation.with.firstName} ${conversation.with.lastName}` : '');
  }

  /** Fewer windows on a narrower screen: keeps those that fit. */
  @HostListener('window:resize')
  fit(): void {
    if (this.windows.length > this.room()) {
      this.windows = this.windows.slice(-this.room());
      this.changes.markForCheck();
    }
  }

  /** How many chat windows fit beside the bar. */
  private room(): number {
    return Math.max(1, Math.min(DOCK_MAX_WINDOWS, Math.floor(globalThis.innerWidth / DOCK_WINDOW_PX) - 1));
  }

  private narrow(): boolean {
    return globalThis.innerWidth < DOCK_WINDOW_PX * 3;
  }
}
