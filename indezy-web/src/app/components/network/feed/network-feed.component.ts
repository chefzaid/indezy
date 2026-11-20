import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, ElementRef, OnInit, inject, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { A11yModule } from '@angular/cdk/a11y';
import { interval } from 'rxjs';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MentionsDirective } from '../mentions/mentions.directive';

import { MemberCard, MemberSuggestion, NetworkPost, TrendingTag, isHeld } from '../../../models';
import { InViewDirective } from '../../../shared/directives/in-view.directive';
import { NetworkNoticesService } from '../../../services/network/network-notices.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { NetworkService } from '../../../services/network/network.service';
import { ConfidentialityGuardService } from '../confidentiality/confidentiality-guard.service';
import { PostKindFieldsComponent } from '../post-kind/post-kind-fields.component';
import { NetworkShare, SHARE_STATE } from '../share/share-to-network.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { FilePickerComponent } from '../files/file-picker.component';
import { PostCardComponent } from '../post-card/post-card.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { suggestionReason } from '../side/suggestion-reason';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';

export const POST_MAX = 3000;
/** How often the feed asks whether newer posts reached it. */
export const NEWER_EVERY_MS = 60_000;
/** Where the composer opens full screen. */
const PHONE = '(max-width: 599.98px)';

/** The home feed (`/network`): writing a post, then the reader's posts and those of the members they follow, newest first. */
/** The feed's tabs: the members and tags followed, For you, and the missions members pass on (each to add to the pipeline). */
export type FeedTab = 'following' | 'forYou' | 'missions';

@Component({
  selector: 'app-network-feed',
  imports: [NetworkNavComponent, NetworkMeCardComponent, PageHeaderComponent, A11yModule, MentionsDirective, InViewDirective, MatTooltipModule, MemberAvatarComponent, PostKindFieldsComponent, ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatProgressSpinnerModule, TranslateModule, PostCardComponent, FilePickerComponent],
  templateUrl: './network-feed.component.html',
  styleUrls: ['./network-feed.component.scss', './network-feed-side.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkFeedComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly confidentiality = inject(ConfidentialityGuardService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  readonly changes = inject(ChangeDetectorRef);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);
  /** The notifications not read yet, counted beside Notifications. */
  readonly unread$ = inject(NetworkNoticesService).unread$;
  /** The conversations with unread messages, counted beside Messages. */
  readonly unreadMessages$ = inject(NetworkNoticesService).messages$;

  readonly max = POST_MAX;
  /** What the post is, and the facts of a mission or an availability. */
  @ViewChild('kindFields') kindFields?: PostKindFieldsComponent;
  @ViewChild('filePicker') filePicker?: FilePickerComponent;
  /** The top of the feed, scrolled back to for the newer posts. */
  @ViewChild('feedTop') feedTop?: ElementRef<HTMLElement>;
  @ViewChild('composerText') composerText?: ElementRef<HTMLTextAreaElement>;
  /** On a phone, the composer is open full screen. */
  composerOpen = false;
  /** What opened the composer, focused again once it closes. */
  private opener: HTMLElement | null = null;
  readonly draft = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(POST_MAX)] });
  posts: NetworkPost[] = [];
  page = 0;
  more = false;
  loading = true;
  /** The next page is on its way. */
  loadingMore = false;
  /** How many newer posts reached the feed since it was read. */
  newer = 0;
  /** The tags trending this week. */
  trending: TrendingTag[] = [];
  notMember = false;
  isPosting = false;
  /** The tags the reader follows, each opening its page. */
  tags: string[] = [];
  /** Members to follow, each with its reason. */
  suggestions: MemberSuggestion[] = [];
  /** The reader's own card, for the composer's avatar and their page. */
  me: MemberCard | null = null;
  /** Whether the composer shows the time to publish the post on its own. */
  scheduling = false;

  ngOnInit(): void {
    this.prefill();
    this.load(0);
    this.network.followedTags().subscribe({ next: tags => this.tags = tags, error: () => this.tags = [] });
    this.network.suggestions().subscribe({ next: suggestions => this.suggestions = suggestions, error: () => this.suggestions = [] });
    this.network.trendingTags().subscribe({ next: trending => this.trending = trending, error: () => this.trending = [] });
    interval(NEWER_EVERY_MS).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.checkNewer());
    this.network.membership().subscribe({ next: membership => this.me = membership.card, error: () => this.me = null });
  }

  /** Why a member is suggested, in words. */
  reason(suggestion: MemberSuggestion): string {
    return suggestionReason(this.translate, suggestion);
  }

  /** Shows the time to publish on its own, or hides it, the time then forgotten. */
  toggleScheduling(): void {
    this.scheduling = !this.scheduling;
    if (!this.scheduling) {
      this.scheduleAt.reset('');
    }
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
    if (NetworkFeedComponent.onPhone()) {
      this.openComposer();
    }
    history.replaceState({ ...state, [SHARE_STATE]: null }, '');
  }

  /** Whether the screen is a phone's, where the composer opens full screen. */
  static onPhone(): boolean {
    return window.matchMedia(PHONE).matches;
  }

  /** On a phone, opens the composer full screen, its text focused. */
  openComposer(): void {
    this.opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.composerOpen = true;
    setTimeout(() => this.composerText?.nativeElement.focus());
  }

  closeComposer(): void {
    if (!this.composerOpen) {
      return;
    }
    this.composerOpen = false;
    setTimeout(() => this.opener?.focus());
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

  /** **Not interested**: the post leaves For you at once, **Undo** bringing it back where it was. */
  notInterested(post: NetworkPost): void {
    const at = this.posts.indexOf(post);
    this.network.notInterested(post.id, true).subscribe({
      next: () => {
        this.posts = this.posts.filter(item => item !== post);
        this.notifications.successWithAction('network.post.notInterestedDone', 'network.post.undo').subscribe(() =>
          this.network.notInterested(post.id, false).subscribe({
            next: () => {
              const posts = [...this.posts];
              posts.splice(Math.max(0, at), 0, post);
              this.posts = posts;
            },
            error: () => this.notifications.error('network.feed.postError')
          }));
      },
      error: () => this.notifications.error('network.feed.postError')
    });
  }

  /** The **Missions** tab's filters; changed, the tab is read again from its first page. */
  readonly missionFilters = inject(FormBuilder).nonNullable.group({
    skill: '', city: '', remote: null as number | null, rate: null as number | null, before: '', hideTaken: false
  });
  readonly remoteChoices = [2, 3, 5];

  /** Reads the missions again with the filters chosen. */
  filterMissions(): void {
    this.load(0);
  }

  /** Picks a minimum of remote days (again to drop it). */
  pickRemote(days: number): void {
    this.missionFilters.controls.remote.setValue(this.missionFilters.controls.remote.value === days ? null : days);
    this.filterMissions();
  }

  toggleHideTaken(): void {
    this.missionFilters.controls.hideTaken.setValue(!this.missionFilters.controls.hideTaken.value);
    this.filterMissions();
  }

  clearMissionFilters(): void {
    this.missionFilters.reset();
    this.filterMissions();
  }

  get filteringMissions(): boolean {
    const value = this.missionFilters.getRawValue();
    return !!(value.skill || value.city || value.remote || value.rate || value.before || value.hideTaken);
  }

  /** Which tab of the feed is shown: the members and tags followed, For you, or the missions members pass on. */
  tab: FeedTab = 'following';

  /** Shows another tab, from its first page. */
  show(tab: FeedTab): void {
    if (tab === this.tab) {
      return;
    }
    this.tab = tab;
    this.loading = true;
    this.newer = 0;
    this.load(0);
  }

  /** The next page, once the end of the feed comes near or **More** is pressed. */
  loadMore(): void {
    if (this.more && !this.loading && !this.loadingMore) {
      this.load(this.page + 1);
    }
  }

  /** Asks whether newer posts reached the Following tab, while the page is seen. */
  checkNewer(): void {
    if (this.tab !== 'following' || this.loading || this.posts.length === 0 || document.hidden) {
      return;
    }
    this.network.newerCount(this.posts[0].createdAt).subscribe({
      next: count => {
        this.newer = count;
        this.changes.markForCheck();
      },
      error: () => this.newer = 0
    });
  }

  /** Reads the feed again from the top, with the newer posts. */
  showNewer(): void {
    this.newer = 0;
    this.load(0);
    this.feedTop?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  load(page: number): void {
    this.loadingMore = page > 0;
    (this.tab === 'forYou' ? this.network.forYou(page) : this.tab === 'missions' ? this.network.missions(page, this.missionFilters.getRawValue()) : this.network.feed(page)).subscribe({
      next: found => {
        this.posts = page === 0 ? found.posts : [...this.posts, ...found.posts];
        this.page = found.page;
        this.more = found.more;
        this.loading = false;
        this.loadingMore = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.loadingMore = false;
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
    if (!text || this.draft.invalid || this.isPosting || (this.filePicker && !this.filePicker.valid)) {
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
    this.network.post(text, kind, this.kindFields?.facts() ?? null, this.kindFields?.poll() ?? null, this.filePicker?.refs() ?? []).subscribe({
      next: post => {
        this.isPosting = false;
        this.draft.reset('');
        this.kindFields?.reset();
        this.filePicker?.clear();
        if (isHeld(post)) {
          this.closeComposer();
          this.notifications.success('network.feed.held');
          return;
        }
        this.posts = [post, ...this.posts];
        this.closeComposer();
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
        this.scheduling = false;
        this.closeComposer();
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
