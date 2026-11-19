import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDialog } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, distinctUntilChanged, map, skip, switchMap } from 'rxjs';

import { MemberCard, MemberPage, NetworkPost } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { ReportDialogComponent } from '../report-dialog/report-dialog.component';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MemberCardComponent } from '../member-card/member-card.component';
import { PostCardComponent } from '../post-card/post-card.component';
import { MemberAvatarComponent, memberGradient } from '../avatar/member-avatar.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { SavedPostsComponent } from '../saved/saved-posts.component';
import { NetworkDraftsComponent } from '../drafts/network-drafts.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkSideComponent } from '../side/network-side.component';

/** Which list of the page is open: the member's followers or follows, or, on the reader's own page, their saved posts or drafts. */
export type MemberList = 'followers' | 'following' | 'saved' | 'drafts';
const LISTS: MemberList[] = ['followers', 'following', 'saved', 'drafts'];

/**
 * A member's page (`/m/<handle>`): the card, the members following them and they follow, the skills they share with
 * the viewer, and **Follow**; on the viewer's own page, removing a follower.
 */
@Component({
  selector: 'app-member-page',
  imports: [SavedPostsComponent, NetworkDraftsComponent, NetworkMeCardComponent, NetworkSideComponent, NetworkNavComponent, MatTooltipModule, MemberAvatarComponent, RouterLink, MatButtonModule, MatIconModule, MatMenuModule, TranslateModule, MemberCardComponent, PostCardComponent],
  templateUrl: './member-page.component.html',
  styleUrls: ['./member-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MemberPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly network = inject(NetworkService);
  private readonly dialog = inject(MatDialog);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly messages = inject(NetworkMessagesService);
  private readonly router = inject(Router);

  page: MemberPage | null = null;
  /** Why the page cannot be shown: the viewer is not a member yet, or no member holds the handle. */
  problem: 'notMember' | 'notFound' | 'error' | null = null;
  loading = true;
  list: MemberList | null = null;
  members: MemberCard[] = [];
  isFollowing = false;
  isOpening = false;
  posts: NetworkPost[] = [];
  postsPage = 0;
  morePosts = false;

  ngOnInit(): void {
    this.route.paramMap.pipe(map(params => params.get('handle') ?? ''), switchMap(handle => {
      this.loading = true;
      this.list = null;
      return this.network.page(handle);
    }), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.page = page;
        this.problem = null;
        this.loading = false;
        this.loadPosts(0);
        this.openTab(this.route.snapshot.queryParamMap.get('tab'));
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.problem = error.status === 409 ? 'notMember' : error.status === 404 ? 'notFound' : 'error';
        this.changes.markForCheck();
      }
    });
    // A tab asked from elsewhere on the same page (the card beside the feed).
    this.route.queryParamMap.pipe(map(params => params.get('tab')), distinctUntilChanged(), skip(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(tab => {
        if (this.page) {
          this.openTab(tab);
          this.changes.markForCheck();
        }
      });
  }

  /** Follow, Ask to follow a private card, Request sent (to withdraw it), or Unfollow. */
  followLabel(page: MemberPage): string {
    if (page.followed) {
      return 'network.page.unfollow';
    }
    if (page.requested) {
      return 'network.page.requested';
    }
    return page.privateCard ? 'network.page.askToFollow' : 'network.page.follow';
  }

  toggleFollow(): void {
    const page = this.page!;
    const request: Observable<MemberPage> = page.followed || page.requested ? this.network.unfollow(page.card.handle) :
      this.network.follow(page.card.handle);
    this.isFollowing = true;
    request.subscribe({
      next: updated => {
        this.page = updated;
        this.isFollowing = false;
        if (this.list === 'followers') {
          this.show('followers');
        }
      },
      error: (error: HttpErrorResponse) => {
        this.isFollowing = false;
        this.notifications.error(error.status === 429 ? 'network.page.tooManyFollows' : 'network.page.followError');
      }
    });
  }

  /** The cover band, in the member's color. */
  get cover(): string {
    return memberGradient(this.page?.card.handle ?? '', 120);
  }

  /** Opens the tab the address names (the saved posts and drafts are the reader's own), else the posts. */
  private openTab(asked: string | null): void {
    const tab = asked as MemberList | null;
    if (tab && LISTS.includes(tab) && (this.page?.own || tab === 'followers' || tab === 'following')) {
      this.show(tab);
    } else {
      this.list = null;
    }
  }

  /** Back to the member's posts from a list. */
  showPosts(): void {
    this.list = null;
  }

  /** Opens a list, or closes it when open. */
  show(list: MemberList): void {
    const handle = this.page!.card.handle;
    this.list = list;
    if (list === 'saved' || list === 'drafts') {
      return;
    }
    (list === 'followers' ? this.network.followers(handle) : this.network.following(handle)).subscribe({
      next: members => this.members = members,
      error: () => this.notifications.error('network.page.listError')
    });
  }

  /** Mutes the member, or no longer. */
  toggleMute(): void {
    const page = this.page!;
    this.network.mute(page.card.handle, !page.muted).subscribe({
      next: updated => {
        this.page = updated;
        this.notifications.success(updated.muted ? 'network.page.mutedDone' : 'network.page.unmutedDone');
      },
      error: () => this.notifications.error('network.page.followError')
    });
  }

  /** Blocks the member once confirmed, or unblocks them. */
  toggleBlock(): void {
    const page = this.page!;
    const block = (): void => {
      this.network.block(page.card.handle, !page.blocked).subscribe({
        next: updated => {
          this.page = updated;
          this.list = null;
          this.notifications.success(updated.blocked ? 'network.page.blockedDone' : 'network.page.unblockedDone');
        },
        error: () => this.notifications.error('network.page.followError')
      });
    };
    if (page.blocked) {
      block();
      return;
    }
    this.confirm.confirm({ messageKey: 'network.page.blockConfirm', messageParams: { name: page.card.firstName }, confirmKey: 'network.page.block',
      danger: true }).subscribe(confirmed => {
      if (confirmed) {
        block();
      }
    });
  }

  loadPosts(page: number): void {
    this.network.memberPosts(this.page!.card.handle, page).subscribe({
      next: found => {
        this.posts = page === 0 ? found.posts : [...this.posts, ...found.posts];
        this.postsPage = found.page;
        this.morePosts = found.more;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.feed.loadError')
    });
  }

  removePost(post: NetworkPost): void {
    this.confirm.confirm({ messageKey: 'network.feed.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (confirmed) {
        this.network.deletePost(post.id).subscribe({
          next: () => this.posts = this.posts.filter(item => item.id !== post.id),
          error: () => this.notifications.error('network.feed.postError')
        });
      }
    });
  }

  removeFollower(member: MemberCard): void {
    this.network.removeFollower(member.handle).subscribe({
      next: () => {
        this.members = this.members.filter(item => item.handle !== member.handle);
        this.page = { ...this.page!, followers: this.page!.followers - 1 };
        this.notifications.success('network.page.followerRemoved');
      },
      error: () => this.notifications.error('network.page.followError')
    });
  }

  /** Opens the conversation with the member, once either follows the other (a follow accepted, not a request waiting). */
  message(): void {
    this.isOpening = true;
    this.messages.open(this.page!.card.handle).subscribe({
      next: conversation => {
        this.isOpening = false;
        void this.router.navigate(['/network/messages', conversation.id]);
      },
      error: () => {
        this.isOpening = false;
        this.notifications.error('network.messages.openError');
      }
    });
  }

  /** Reports the member to the moderators. */
  report(): void {
    ReportDialogComponent.open(this.dialog, { target: 'members', id: this.page!.card.handle }).subscribe();
  }
}
