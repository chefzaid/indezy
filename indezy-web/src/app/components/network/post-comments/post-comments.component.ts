import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';

import { NetworkComment, NetworkPost, isHeld } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';

export const COMMENT_MAX = 1500;

/**
 * The comments under a post: the pinned one first, each with its replies, a box to comment or reply, and the actions of
 * each comment's writer (delete) and of the post's author (pin, hide).
 */
@Component({
  selector: 'app-post-comments',
  imports: [DatePipe, NgTemplateOutlet, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule,
    TranslateModule],
  templateUrl: './post-comments.component.html',
  styleUrls: ['./post-comments.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PostCommentsComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly confidentiality = inject(ConfidentialityGuardService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);

  @Input({ required: true }) post!: NetworkPost;
  /** How many comments the reader sees once one changed here, for the post's count. */
  @Output() readonly counted = new EventEmitter<number>();

  readonly max = COMMENT_MAX;
  readonly draft = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(COMMENT_MAX)] });
  comments: NetworkComment[] = [];
  /** The comment being answered; none to comment the post itself. */
  replyTo: NetworkComment | null = null;
  /** The comment whose like is being saved. */
  liking: number | null = null;
  isSending = false;

  ngOnInit(): void {
    // The post brings its count along: only a change made here tells it again.
    this.load(false);
  }

  load(recount = true): void {
    this.network.comments(this.post.id).subscribe({
      next: comments => {
        this.comments = comments;
        if (recount) {
          this.counted.emit(comments.reduce((count, comment) => count + 1 + comment.replies.length, 0));
        }
      },
      error: () => this.notifications.error('network.post.commentError')
    });
  }

  send(): void {
    const text = this.draft.value.trim();
    if (!text || this.isSending) {
      return;
    }
    this.isSending = true;
    this.confidentiality.review(text).subscribe(choice => {
      if (choice?.action === 'publish') {
        this.commentNow(choice.text);
        return;
      }
      this.isSending = false;
      if (choice) {
        this.draft.setValue(choice.text);
        this.notifications.success('network.confidentiality.anonymized');
      }
    });
  }

  /** Comments the text checked for the workspace's names. */
  private commentNow(text: string): void {
    this.network.comment(this.post.id, text, this.replyTo?.id ?? null).subscribe({
      next: answer => {
        this.isSending = false;
        this.draft.reset('');
        this.replyTo = null;
        if (isHeld(answer)) {
          this.notifications.success('network.feed.held');
          return;
        }
        this.load();
      },
      error: (error: HttpErrorResponse) => {
        this.isSending = false;
        this.notifications.error(error.status === 429 ? 'network.post.tooManyComments' : 'network.post.commentError');
      }
    });
  }

  /** Likes the comment, or no longer, keeping its place in the thread. */
  toggleLike(comment: NetworkComment): void {
    this.liking = comment.id;
    this.network.likeComment(comment.id, !comment.liked).subscribe({
      next: updated => {
        this.liking = null;
        comment.likes = updated.likes;
        comment.liked = updated.liked;
      },
      error: () => {
        this.liking = null;
        this.notifications.error('network.post.likeError');
      }
    });
  }

  remove(comment: NetworkComment): void {
    this.confirm.confirm({ messageKey: 'network.post.deleteComment', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.network.deleteComment(comment.id).subscribe({ next: () => this.load(), error: () => this.notifications.error('network.post.commentError') });
      }
    });
  }

  hide(comment: NetworkComment): void {
    this.network.hideComment(comment.id, !comment.hidden).subscribe({
      next: () => this.load(),
      error: () => this.notifications.error('network.post.commentError')
    });
  }

  pin(comment: NetworkComment): void {
    this.network.pinComment(this.post.id, comment.pinned ? null : comment.id).subscribe({
      next: () => this.load(),
      error: () => this.notifications.error('network.post.commentError')
    });
  }

  /** Marks a reply as the answer to the reader's question, or no longer. */
  markAnswer(comment: NetworkComment): void {
    const answer = comment.id === this.post.answerCommentId ? null : comment.id;
    this.network.answer(this.post.id, answer).subscribe({
      next: () => this.post.answerCommentId = answer,
      error: () => this.notifications.error('network.post.commentError')
    });
  }

  /** Reports another member's comment to the moderators. */
  report(comment: NetworkComment): void {
    ReportDialogComponent.open(this.dialog, { target: 'comments', id: comment.id }).subscribe();
  }
}
