import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, catchError, distinctUntilChanged, forkJoin, map, of } from 'rxjs';

import { MemberCard, MemberDirectory, NetworkPost, NetworkPosts } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { PostCardComponent } from '../post-card/post-card.component';
import { NetworkSideComponent } from '../side/network-side.component';

/** The tabs of the results, as on LinkedIn: everything found, then members, posts or tags alone. */
export type SearchTab = 'all' | 'members' | 'posts' | 'tags';
const TABS: SearchTab[] = ['all', 'members', 'posts', 'tags'];
/** How many members and posts **All** shows before **See all**. */
export const SEARCH_PREVIEW = 3;

/**
 * The network's search results (`/network/search?q=`, the tab in `?tab=`): the members every word typed names (by name,
 * handle, skill, city or headline), each to follow from the list; the posts holding what was typed; and the tags in use
 * starting with it. **All** shows the first of each, with **See all** for the rest.
 */
@Component({
  selector: 'app-network-search-results',
  imports: [RouterLink, MatButtonModule, MatIconModule, TranslateModule, MemberAvatarComponent, NetworkMeCardComponent, NetworkNavComponent,
    PostCardComponent, NetworkSideComponent],
  templateUrl: './network-search-results.component.html',
  styleUrls: ['./network-search-results.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkSearchResultsComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly tabs = TABS;
  readonly preview = SEARCH_PREVIEW;
  query = '';
  tab: SearchTab = 'all';
  loading = false;
  members: MemberCard[] = [];
  membersTotal = 0;
  membersPage = 0;
  posts: NetworkPost[] = [];
  postsPage = 0;
  morePosts = false;
  tags: string[] = [];
  private followed = new Set<string>();
  private requested = new Set<string>();
  private privateCards = new Set<string>();
  readonly pending = new Set<string>();

  ngOnInit(): void {
    this.route.queryParamMap.pipe(map(params => ({ q: (params.get('q') ?? '').trim(), tab: params.get('tab') })),
      distinctUntilChanged((a, b) => a.q === b.q && a.tab === b.tab), takeUntilDestroyed(this.destroyRef))
      .subscribe(({ q, tab }) => {
        this.tab = TABS.includes(tab as SearchTab) ? tab as SearchTab : 'all';
        if (q !== this.query || !this.loading && this.members.length + this.posts.length + this.tags.length === 0) {
          this.query = q;
          this.search();
        }
        this.changes.markForCheck();
      });
  }

  /** Opens a tab, kept in the address. */
  open(tab: SearchTab): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: tab === 'all' ? null : tab }, queryParamsHandling: 'merge' });
  }

  get nothing(): boolean {
    return !this.loading && this.members.length === 0 && this.posts.length === 0 && this.tags.length === 0;
  }

  private search(): void {
    this.members = [];
    this.posts = [];
    this.tags = [];
    this.membersTotal = 0;
    this.morePosts = false;
    if (this.query.replace(/^[@#]/, '').length < 2) {
      this.loading = false;
      return;
    }
    this.loading = true;
    const word = this.query.replace(/^[@#]/, '');
    forkJoin([
      this.network.searchMembers(this.query, 0).pipe(catchError(() => this.failed<MemberDirectory | null>(null))),
      this.network.searchPosts(this.query, 0).pipe(catchError(() => this.failed<NetworkPosts | null>(null))),
      /^[\p{L}\p{N}_-]+$/u.test(word) ? this.network.suggestTags(word).pipe(catchError(() => of([] as string[]))) : of([] as string[])
    ]).subscribe(([members, posts, tags]) => {
      this.loading = false;
      if (members) {
        this.takeMembers(members, false);
      }
      if (posts) {
        this.takePosts(posts, false);
      }
      this.tags = tags;
      this.changes.markForCheck();
    });
  }

  private failed<T>(none: T): Observable<T> {
    this.notifications.error('network.search.error');
    return of(none);
  }

  /** The next members found. */
  moreMembers(): void {
    this.network.searchMembers(this.query, this.membersPage + 1).subscribe({
      next: found => this.takeMembers(found, true),
      error: () => this.notifications.error('network.search.error')
    });
  }

  /** The next posts found. */
  loadMorePosts(): void {
    this.network.searchPosts(this.query, this.postsPage + 1).subscribe({
      next: found => this.takePosts(found, true),
      error: () => this.notifications.error('network.search.error')
    });
  }

  get hasMoreMembers(): boolean {
    return this.members.length < this.membersTotal;
  }

  private takeMembers(found: MemberDirectory, append: boolean): void {
    this.members = append ? [...this.members, ...found.members] : found.members;
    this.membersTotal = found.total;
    this.membersPage = found.page;
    found.followed.forEach(handle => this.followed.add(handle));
    found.requested.forEach(handle => this.requested.add(handle));
    found.privateCards.forEach(handle => this.privateCards.add(handle));
    this.changes.markForCheck();
  }

  private takePosts(found: NetworkPosts, append: boolean): void {
    this.posts = append ? [...this.posts, ...found.posts] : found.posts;
    this.postsPage = found.page;
    this.morePosts = found.more;
    this.changes.markForCheck();
  }

  removePost(post: NetworkPost): void {
    this.posts = this.posts.filter(item => item.id !== post.id);
  }

  /** Follow, Ask to follow a private card, Request sent, or Following. */
  followLabel(handle: string): string {
    if (this.followed.has(handle)) {
      return 'network.directory.following';
    }
    if (this.requested.has(handle)) {
      return 'network.page.requested';
    }
    return this.privateCards.has(handle) ? 'network.page.askToFollow' : 'network.page.follow';
  }

  isFollowing(handle: string): boolean {
    return this.followed.has(handle) || this.requested.has(handle);
  }

  /** Follows a member found, asks to for a private card, or undoes either. */
  toggleFollow(handle: string): void {
    const undo = this.isFollowing(handle);
    this.pending.add(handle);
    (undo ? this.network.unfollow(handle) : this.network.follow(handle)).subscribe({
      next: page => {
        this.pending.delete(handle);
        this.followed.delete(handle);
        this.requested.delete(handle);
        if (page.followed) {
          this.followed.add(handle);
        } else if (page.requested) {
          this.requested.add(handle);
        }
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.pending.delete(handle);
        this.notifications.error(error.status === 429 ? 'network.page.tooManyFollows' : 'network.page.followError');
        this.changes.markForCheck();
      }
    });
  }
}
