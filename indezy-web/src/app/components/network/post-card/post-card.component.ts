import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';

/** A piece of a post's text: plain text, a member mentioned, or a #tag. */
export type PostSegment = { text: string } | { handle: string } | { tag: string };

/** The @handles and #tags of a post's text, the rest kept as written. */
const TOKEN = /(@[a-z][a-z0-9_]{2,29})|(#[\p{L}\p{N}][\p{L}\p{N}_-]{0,39})/gu;

/** Splits a post's text into plain text, mentions (linking to the member's page) and #tags; nothing is read as HTML. */
export function segments(text: string): PostSegment[] {
  const parts: PostSegment[] = [];
  let last = 0;
  for (const match of text.matchAll(TOKEN)) {
    const start = match.index!;
    const before = start === 0 ? '' : text[start - 1];
    if (/[\p{L}\p{N}_]/u.test(before)) {
      continue;
    }
    if (start > last) {
      parts.push({ text: text.slice(last, start) });
    }
    parts.push(match[1] ? { handle: match[1].slice(1) } : { tag: match[2].slice(1) });
    last = start + match[0].length;
  }
  if (last < text.length) {
    parts.push({ text: text.slice(last) });
  }
  return parts;
}

/** A post: its author (opening their page), when it was posted, its text with mentions and tags, and its own actions. */
@Component({
  selector: 'app-post-card',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, MatMenuModule, TranslateModule],
  templateUrl: './post-card.component.html',
  styleUrls: ['./post-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PostCardComponent {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);

  @Input({ required: true }) post!: NetworkPost;
  /** The members who like the post, once asked for. */
  likers: MemberCard[] | null = null;
  isLiking = false;

  toggleLike(): void {
    this.isLiking = true;
    this.network.like(this.post.id, !this.post.liked).subscribe({
      next: post => {
        this.post = post;
        this.isLiking = false;
        if (this.likers) {
          this.showLikers();
        }
      },
      error: () => {
        this.isLiking = false;
        this.notifications.error('network.post.likeError');
      }
    });
  }

  /** Shows who likes the post, or hides the list. */
  showLikers(): void {
    this.network.likers(this.post.id).subscribe({
      next: likers => this.likers = likers,
      error: () => this.notifications.error('network.post.likeError')
    });
  }
  /** Asked to delete the post, its author's own. */
  @Output() readonly deleted = new EventEmitter<NetworkPost>();

  get parts(): PostSegment[] {
    return segments(this.post.text);
  }

  textOf(part: PostSegment): string | null {
    return 'text' in part ? part.text : null;
  }

  handleOf(part: PostSegment): string | null {
    return 'handle' in part ? part.handle : null;
  }

  tagOf(part: PostSegment): string | null {
    return 'tag' in part ? part.tag : null;
  }
}
