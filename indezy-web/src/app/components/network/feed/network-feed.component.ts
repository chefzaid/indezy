import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { MemberSuggestion, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PostCardComponent } from '../post-card/post-card.component';

export const POST_MAX = 3000;

/** The home feed (`/network`): writing a post, then the reader's posts and those of the members they follow, newest first. */
@Component({
  selector: 'app-network-feed',
  imports: [PageHeaderComponent, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule, PostCardComponent],
  templateUrl: './network-feed.component.html',
  styleUrls: ['./network-feed.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkFeedComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly max = POST_MAX;
  readonly draft = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(POST_MAX)] });
  posts: NetworkPost[] = [];
  page = 0;
  more = false;
  loading = true;
  notMember = false;
  isPosting = false;
  /** The tags the reader follows, each opening its page. */
  tags: string[] = [];
  /** Members to follow, each with its reason. */
  suggestions: MemberSuggestion[] = [];

  ngOnInit(): void {
    this.load(0);
    this.network.followedTags().subscribe({ next: tags => this.tags = tags, error: () => this.tags = [] });
    this.network.suggestions().subscribe({ next: suggestions => this.suggestions = suggestions, error: () => this.suggestions = [] });
  }

  /** Follows a suggested member, who leaves the suggestions, and reads the feed again with their posts. */
  follow(suggestion: MemberSuggestion): void {
    this.network.follow(suggestion.card.handle).subscribe({
      next: () => {
        this.suggestions = this.suggestions.filter(item => item !== suggestion);
        this.load(0);
      },
      error: (error: HttpErrorResponse) =>
        this.notifications.error(error.status === 429 ? 'network.page.tooManyFollows' : 'network.page.followError')
    });
  }

  load(page: number): void {
    this.network.feed(page).subscribe({
      next: found => {
        this.posts = page === 0 ? found.posts : [...this.posts, ...found.posts];
        this.page = found.page;
        this.more = found.more;
        this.loading = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.notMember = error.status === 409;
        if (!this.notMember) {
          this.notifications.error('network.feed.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  publish(): void {
    const text = this.draft.value.trim();
    if (!text || this.draft.invalid || this.isPosting) {
      this.draft.markAsTouched();
      return;
    }
    this.isPosting = true;
    this.network.post(text).subscribe({
      next: post => {
        this.isPosting = false;
        this.posts = [post, ...this.posts];
        this.draft.reset('');
        this.notifications.success('network.feed.posted');
      },
      error: (error: HttpErrorResponse) => {
        this.isPosting = false;
        this.notifications.error(error.status === 429 ? 'network.feed.tooManyPosts' : 'network.feed.postError');
      }
    });
  }

  remove(post: NetworkPost): void {
    this.confirm.confirm({ messageKey: 'network.feed.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.network.deletePost(post.id).subscribe({
        next: () => {
          this.posts = this.posts.filter(item => item.id !== post.id);
          this.notifications.success('network.feed.deleted');
        },
        error: () => this.notifications.error('network.feed.postError')
      });
    });
  }
}
