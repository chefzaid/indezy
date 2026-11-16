import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { interval } from 'rxjs';

import { Conversation } from '../../../models';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { TimeAgoPipe } from '../time-ago/time-ago.pipe';
import { MessageThreadComponent } from './message-thread.component';

/** How often the list of conversations is read again. */
export const CONVERSATIONS_REFRESH_MS = 30_000;

/**
 * Messages (`/network/messages`, `/network/messages/<id>`): the reader's conversations, the latest first, beside the one
 * open; on a phone, the list or the conversation.
 */
@Component({
  selector: 'app-messages-page',
  imports: [MemberAvatarComponent, MessageThreadComponent, TimeAgoPipe, RouterLink, MatIconModule, TranslateModule],
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

  conversations: Conversation[] = [];
  archived = false;
  loading = true;
  notMember = false;
  /** The conversation open beside the list. */
  openId: number | null = null;

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const id = params.get('id');
      this.openId = id ? Number(id) : null;
      this.changes.markForCheck();
    });
    this.load();
    interval(CONVERSATIONS_REFRESH_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (!document.hidden) {
        this.load();
      }
    });
  }

  load(): void {
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

  /** The inbox, or the archived conversations. */
  showArchived(archived: boolean): void {
    if (archived !== this.archived) {
      this.archived = archived;
      this.loading = true;
      this.load();
    }
  }

  /** The open conversation changed: its row follows, moved to the top when a message went, gone when it left this list. */
  updated(conversation: Conversation): void {
    const others = this.conversations.filter(item => item.id !== conversation.id);
    const belongs = conversation.archived === this.archived;
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
