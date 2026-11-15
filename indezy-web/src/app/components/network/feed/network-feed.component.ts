import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject, ViewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';

import { MemberSuggestion, NetworkPost, isHeld } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import { PostKindFieldsComponent } from '../post-kind/post-kind-fields.component';
import { NetworkShare, SHARE_STATE } from '../share/share-to-network.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PostCardComponent } from '../post-card/post-card.component';

export const POST_MAX = 3000;

/** The home feed (`/network`): writing a post, then the reader's posts and those of the members they follow, newest first. */
@Component({
  selector: 'app-network-feed',
  imports: [MatButtonToggleModule, PostKindFieldsComponent, PageHeaderComponent, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule, PostCardComponent],
  templateUrl: './network-feed.component.html',
  styleUrls: ['./network-feed.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkFeedComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly confidentiality = inject(ConfidentialityGuardService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly max = POST_MAX;
  /** What the post is, and the facts of a mission or an availability. */
  @ViewChild('kindFields') kindFields?: PostKindFieldsComponent;
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
    this.prefill();
    this.load(0);
    this.network.followedTags().subscribe({ next: tags => this.tags = tags, error: () => this.tags = [] });
    this.network.suggestions().subscribe({ next: suggestions => this.suggestions = suggestions, error: () => this.suggestions = [] });
  }

  /** A draft shared from elsewhere in the app, read once: the text, and the kind with its facts. */
  shared: NetworkShare | null = null;

  /** Fills the composer with what another page shared, to read again before publishing. */
  private prefill(): void {
    const state = history.state as Record<string, unknown> | null;
    const share = state?.[SHARE_STATE] as NetworkShare | undefined;
    if (!share) {
      return;
    }
    this.shared = share;
    this.draft.setValue(share.text.slice(0, POST_MAX));
    history.replaceState({ ...state, [SHARE_STATE]: null }, '');
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

  /** Which tab of the feed is shown: the members and tags followed, or For you. */
  tab: 'following' | 'forYou' = 'following';

  /** Shows the other tab, from its first page. */
  show(tab: 'following' | 'forYou'): void {
    if (tab === this.tab) {
      return;
    }
    this.tab = tab;
    this.loading = true;
    this.load(0);
  }

  load(page: number): void {
    (this.tab === 'forYou' ? this.network.forYou(page) : this.network.feed(page)).subscribe({
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
    this.confidentiality.review(text).subscribe(choice => {
      if (choice?.action === 'publish') {
        this.postNow(choice.text);
        return;
      }
      this.isPosting = false;
      if (choice) {
        this.draft.setValue(choice.text);
        this.notifications.success('network.confidentiality.anonymized');
      }
    });
  }

  /** Posts the text checked for the workspace's names. */
  private postNow(text: string): void {
    const kind = this.kindFields?.kind.value ?? 'POST';
    this.network.post(text, kind, this.kindFields?.facts() ?? null, this.kindFields?.poll() ?? null).subscribe({
      next: post => {
        this.isPosting = false;
        this.draft.reset('');
        this.kindFields?.reset();
        if (isHeld(post)) {
          this.notifications.success('network.feed.held');
          return;
        }
        this.posts = [post, ...this.posts];
        this.notifications.success('network.feed.posted');
      },
      error: (error: HttpErrorResponse) => {
        this.isPosting = false;
        this.notifications.error(error.status === 429 ? 'network.feed.tooManyPosts' : 'network.feed.postError');
      }
    });
  }

  /** The time chosen to publish the post on its own, as a local date and time; none to post now or keep a draft. */
  readonly scheduleAt = new FormControl('', { nonNullable: true });

  /** Keeps the text as a draft, scheduled when a time is chosen. */
  keep(): void {
    const text = this.draft.value.trim();
    if (!text || this.isPosting) {
      return;
    }
    const at = this.scheduleAt.value ? new Date(this.scheduleAt.value).toISOString() : null;
    this.isPosting = true;
    this.network.createDraft(text, at).subscribe({
      next: () => {
        this.isPosting = false;
        this.draft.reset('');
        this.scheduleAt.reset('');
        this.notifications.success(at ? 'network.drafts.scheduledDone' : 'network.drafts.keptDone');
      },
      error: (error: HttpErrorResponse) => {
        this.isPosting = false;
        this.notifications.error(error.status === 400 ? 'network.drafts.scheduleError' : 'network.drafts.saveError');
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
