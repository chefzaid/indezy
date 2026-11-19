import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MentionsDirective } from '../mentions/mentions.directive';

import { MemberCard, NetworkPost, NetworkPostVersion, POST_EDIT_WINDOW, Poll, PostDetails, PostStats, REACTIONS } from '../../../models';
import { PostImpressionsService } from '../../../services/network/post-impressions.service';
import { SendPostDialogComponent } from '../send-post/send-post-dialog.component';
import { ReactionButtonComponent } from './reaction-button.component';
import { NetworkService } from '../../../services/network/network.service';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { NetworkFilesComponent } from '../files/network-files.component';
import { PostCommentsComponent } from '../post-comments/post-comments.component';
import { MissionTakeComponent } from '../mission-take/mission-take.component';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { TimeAgoPipe } from '../time-ago/time-ago.pipe';
import { POST_KIND_ICONS } from '../post-kind/post-kind-fields.component';

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
  imports: [ReactionButtonComponent, MentionsDirective, MemberAvatarComponent, TimeAgoPipe, MissionTakeComponent, DatePipe, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule,
    TranslateModule, PostCommentsComponent, NetworkFilesComponent],
  templateUrl: './post-card.component.html',
  styleUrls: ['./post-card.component.scss', './post-card-content.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PostCardComponent implements AfterViewInit, OnDestroy {
  private readonly network = inject(NetworkService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly translate = inject(TranslateService);
  private readonly impressions = inject(PostImpressionsService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cardChanges = inject(ChangeDetectorRef);
  private observer?: IntersectionObserver;
  /** The post's analytics, for its author, once asked for. */
  insights: PostStats | null = null;
  loadingInsights = false;

  @Input({ required: true }) post!: NetworkPost;
  /** Whether the post is the one its author pinned on their page (shown there with a badge). */
  @Input() pinned = false;
  /** The author pinned or unpinned the post. */
  @Output() readonly pinChanged = new EventEmitter<boolean>();
  /** The members who like the post, once asked for. */
  likers: MemberCard[] | null = null;
  /** The icon of each kind of post, on its badge. */
  readonly kindIcons = POST_KIND_ICONS;
  isVoting = false;
  /** Whether the comments are open under the post. */
  showComments = false;

  /** Opens the comments at once, as a post's own page does. */
  @Input() set commentsOpen(open: boolean) {
    this.showComments = open;
  }
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

  /** Saves the post to the reader's private list, or takes it off. */
  toggleBookmark(): void {
    const before = this.post;
    this.post = { ...before, bookmarked: !before.bookmarked };
    this.network.bookmark(before.id, !before.bookmarked).subscribe({
      next: post => {
        this.post = post;
        this.unsaved.emit(post);
      },
      error: () => {
        this.post = before;
        this.notifications.error('network.post.likeError');
      }
    });
  }

  /** The post as the reader's reaction left it; the list of who reacted follows. */
  reacted(post: NetworkPost): void {
    this.post = post;
    if (this.likers) {
      this.showLikers();
    }
  }

  /** The emoji of the most frequent reactions, three at most. */
  topReactions(): string[] {
    const shown = (this.post.reactions ?? []).slice(0, 3).map(item => REACTIONS.find(known => known.reaction === item.reaction)!.emoji);
    return shown.length ? shown : ['❤️'];
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

  /** Reports another member's post to the moderators. */
  /** Copies the address of the post's own page, to share it. */
  copyLink(): void {
    const link = `${window.location.origin}/network/posts/${this.post.id}`;
    navigator.clipboard.writeText(link).then(() => this.notifications.success('network.post.linkCopied'),
      () => this.notifications.error('network.post.linkCopyError'));
  }

  report(): void {
    ReportDialogComponent.open(this.dialog, { target: 'posts', id: this.post.id }).subscribe();
  }

  /** The daily rate range as read: "600–700 €", "from 600 €", "up to 700 €". */
  rate(facts: PostDetails): string {
    if (facts.rateMin !== null && facts.rateMax !== null) {
      return facts.rateMin === facts.rateMax ? `${facts.rateMin} €` : `${facts.rateMin}–${facts.rateMax} €`;
    }
    return facts.rateMin !== null ? this.translate.instant('network.kinds.rateFrom', { rate: facts.rateMin }) :
      this.translate.instant('network.kinds.rateUpTo', { rate: facts.rateMax });
  }

  /** Votes for a choice of the poll, or changes the vote. */
  vote(choice: number): void {
    this.isVoting = true;
    this.network.vote(this.post.id, choice).subscribe({
      next: updated => {
        this.isVoting = false;
        this.post = updated;
      },
      error: () => {
        this.isVoting = false;
        this.notifications.error('network.poll.voteError');
      }
    });
  }

  /** The share of the votes a choice has, in whole percents. */
  share(poll: Poll, index: number): number {
    return poll.total ? Math.round(100 * (poll.choices[index].votes ?? 0) / poll.total) : 0;
  }

  /** Counts the post shown to its reader once half of it is in view (an author's own posts aside), for its author's analytics. */
  ngAfterViewInit(): void {
    if (this.post.own || typeof IntersectionObserver === 'undefined') {
      return;
    }
    this.observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        this.impressions.seen(this.post.id);
        this.observer?.disconnect();
      }
    }, { threshold: 0.5 });
    this.observer.observe(this.host.nativeElement);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }

  /** Shows the post's analytics to its author, or hides them. */
  toggleInsights(): void {
    if (this.insights) {
      this.insights = null;
      return;
    }
    this.loadingInsights = true;
    this.network.postStats(this.post.id).subscribe({
      next: stats => {
        this.insights = stats;
        this.loadingInsights = false;
        this.cardChanges.markForCheck();
      },
      error: () => {
        this.loadingInsights = false;
        this.notifications.error('network.insights.loadError');
        this.cardChanges.markForCheck();
      }
    });
  }

  /** Pins the post at the top of its author's page, replacing the one pinned, or unpins it. */
  togglePin(): void {
    const pin = !this.pinned;
    this.network.pinPost(this.post.id, pin).subscribe({
      next: () => {
        this.pinned = pin;
        this.pinChanged.emit(pin);
        this.notifications.success(pin ? 'network.post.pinnedDone' : 'network.post.unpinnedDone');
        this.cardChanges.markForCheck();
      },
      error: () => this.notifications.error('network.post.pinError')
    });
  }

  /** Sends the post in a message to one or more conversations or members. */
  send(): void {
    SendPostDialogComponent.open(this.dialog, this.post).subscribe(sent => {
      if (sent) {
        this.notifications.success(sent === 1 ? 'network.send.doneOne' : 'network.send.done', undefined, { count: sent });
      }
    });
  }
}
