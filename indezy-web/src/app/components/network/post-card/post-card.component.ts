import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard, NetworkPost, NetworkPostVersion, POST_EDIT_WINDOW } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PostCommentsComponent } from '../post-comments/post-comments.component';

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
  imports: [DatePipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule,
    TranslateModule, PostCommentsComponent],
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
  /** Whether the comments are open under the post. */
  showComments = false;
  /** The text being written while the author edits the post; none otherwise. */
  editing: FormControl<string> | null = null;
  /** The post's earlier texts, once asked for. */
  versions: NetworkPostVersion[] | null = null;

  /** The reader's own post, in the 15 minutes after it was posted. */
  get editable(): boolean {
    return this.post.own && Date.now() - Date.parse(this.post.createdAt) < POST_EDIT_WINDOW;
  }

  startEditing(): void {
    this.editing = new FormControl(this.post.text, { nonNullable: true, validators: [Validators.required, Validators.maxLength(3000)] });
  }

  saveEdit(): void {
    const text = this.editing?.value.trim();
    if (!text) {
      return;
    }
    this.network.editPost(this.post.id, text).subscribe({
      next: post => {
        this.post = post;
        this.editing = null;
        this.versions = null;
      },
      error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'network.post.editTooLate' : 'network.post.editError')
    });
  }

  /** Shows the post's earlier texts, or hides them. */
  toggleVersions(): void {
    if (this.versions) {
      this.versions = null;
      return;
    }
    this.network.postVersions(this.post.id).subscribe({
      next: versions => this.versions = versions,
      error: () => this.notifications.error('network.post.editError')
    });
  }

  /** Closes the comments of the reader's own post, or opens them again. */
  toggleClosed(): void {
    const closed = !this.post.commentsClosed;
    this.network.closeComments(this.post.id, closed).subscribe({
      next: () => this.post = { ...this.post, commentsClosed: closed },
      error: () => this.notifications.error('network.post.commentError')
    });
  }

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

  /** Saves the post to the reader's private list, or takes it off. */
  toggleBookmark(): void {
    this.network.bookmark(this.post.id, !this.post.bookmarked).subscribe({
      next: post => {
        this.post = post;
        this.unsaved.emit(post);
      },
      error: () => this.notifications.error('network.post.likeError')
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
  /** Told once the reader saves the post or takes it off their list. */
  @Output() readonly unsaved = new EventEmitter<NetworkPost>();
  /** Told of the repost the reader just made of the post. */
  @Output() readonly reposted = new EventEmitter<NetworkPost>();
  /** The comment being written while the reader reposts the post; none otherwise. */
  reposting: FormControl<string> | null = null;

  startReposting(): void {
    this.reposting = new FormControl('', { nonNullable: true, validators: [Validators.maxLength(3000)] });
  }

  /** Reposts the post with the comment written, if any. */
  repost(): void {
    this.network.repost(this.post.id, this.reposting?.value.trim() ?? '').subscribe({
      next: repost => {
        this.reposting = null;
        this.post = { ...this.post, reposts: this.post.reposts + 1 };
        this.notifications.success('network.post.repostedDone');
        this.reposted.emit(repost);
      },
      error: (error: HttpErrorResponse) => this.notifications.error(error.status === 409 ? 'network.post.repostsOffError' :
        error.status === 429 ? 'network.feed.tooManyPosts' : 'network.post.editError')
    });
  }

  /** Turns reposts of the reader's own post off, or on again. */
  toggleRepostsOff(): void {
    this.network.repostsOff(this.post.id, !this.post.repostsOff).subscribe({
      next: post => this.post = post,
      error: () => this.notifications.error('network.post.editError')
    });
  }

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
