import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { TranslateModule } from '@ngx-translate/core';
import { map, switchMap } from 'rxjs';

import { CommunityMember, CommunityPage, NetworkPost } from '../../../models';
import { NetworkCommunitiesService } from '../../../services/network/network-communities.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { PostCardComponent } from '../post-card/post-card.component';
import { NetworkSideComponent } from '../side/network-side.component';

/**
 * A community (`/network/communities/<slug>`): its name and description, **Join** or **Leave**, its feed with a composer
 * for its members, and its members; its moderators remove posts and members, its creator names co-moderators, and the
 * administrators do all of it in every community.
 */
@Component({
  selector: 'app-network-community',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule,
    TranslateModule, MemberAvatarComponent, NetworkMeCardComponent, NetworkNavComponent, PostCardComponent, NetworkSideComponent],
  templateUrl: './network-community.component.html',
  styleUrls: ['./network-community.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkCommunityComponent implements OnInit {
  private readonly communities = inject(NetworkCommunitiesService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  slug = '';
  page: CommunityPage | null = null;
  missing = false;
  tab: 'posts' | 'members' = 'posts';
  posts: NetworkPost[] = [];
  postsPage = 0;
  morePosts = false;
  members: CommunityMember[] | null = null;
  readonly text = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(3000)] });
  posting = false;
  editing = false;
  readonly name = new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(60)] });
  readonly description = new FormControl('', { nonNullable: true, validators: Validators.maxLength(500) });

  ngOnInit(): void {
    this.route.paramMap.pipe(map(params => params.get('slug') ?? ''), switchMap(slug => {
      this.slug = slug;
      this.page = null;
      this.missing = false;
      this.tab = 'posts';
      this.members = null;
      return this.communities.page(slug);
    }), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: page => {
        this.page = page;
        this.loadPosts(0);
        this.changes.markForCheck();
      },
      error: () => {
        this.missing = true;
        this.changes.markForCheck();
      }
    });
  }

  loadPosts(page: number): void {
    this.communities.posts(this.slug, page).subscribe({
      next: found => {
        this.posts = page === 0 ? found.posts : [...this.posts, ...found.posts];
        this.postsPage = found.page;
        this.morePosts = found.more;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.communities.loadError')
    });
  }

  showMembers(): void {
    this.tab = 'members';
    this.communities.members(this.slug).subscribe({
      next: members => {
        this.members = members;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.communities.loadError')
    });
  }

  /** Joins, or leaves, the community. */
  toggleMembership(): void {
    const joined = this.page!.community.joined;
    (joined ? this.communities.leave(this.slug) : this.communities.join(this.slug)).subscribe({
      next: page => {
        this.page = page;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) =>
        this.notifications.error(error.status === 409 ? 'network.communities.removedFrom' : 'network.communities.saveError')
    });
  }

  /** Posts in the community, on top of its feed. */
  publish(): void {
    if (this.text.invalid || this.posting) {
      return;
    }
    this.posting = true;
    this.communities.post(this.slug, this.text.value.trim()).subscribe({
      next: post => {
        this.posting = false;
        this.text.reset();
        if (post) {
          this.posts = [post, ...this.posts];
        } else {
          this.notifications.success('network.feed.held');
        }
        this.changes.markForCheck();
      },
      error: () => {
        this.posting = false;
        this.notifications.error('network.feed.postError');
        this.changes.markForCheck();
      }
    });
  }

  /** A moderator takes a post out of the community, deleting it, once confirmed. */
  removePost(post: NetworkPost): void {
    this.confirm.confirm({ messageKey: 'network.communities.removePostConfirm', confirmKey: 'common.delete', danger: true })
      .subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        this.communities.removePost(this.slug, post.id).subscribe({
          next: () => {
            this.posts = this.posts.filter(item => item.id !== post.id);
            this.changes.markForCheck();
          },
          error: () => this.notifications.error('network.communities.saveError')
        });
      });
  }

  /** A moderator removes a member, who cannot join again, once confirmed. */
  removeMember(member: CommunityMember): void {
    this.confirm.confirm({ messageKey: 'network.communities.removeMemberConfirm', messageParams: { name: member.card.firstName },
      confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.communities.removeMember(this.slug, member.card.handle).subscribe({
        next: () => {
          this.members = (this.members ?? []).filter(item => item !== member);
          this.page = { ...this.page!, community: { ...this.page!.community, members: this.page!.community.members - 1 } };
          this.changes.markForCheck();
        },
        error: () => this.notifications.error('network.communities.saveError')
      });
    });
  }

  /** The creator names a co-moderator, or no longer. */
  toggleModerator(member: CommunityMember): void {
    this.communities.setModerator(this.slug, member.card.handle, !member.moderator).subscribe({
      next: members => {
        this.members = members;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.communities.saveError')
    });
  }

  startEditing(): void {
    this.name.setValue(this.page!.community.name);
    this.description.setValue(this.page!.community.description ?? '');
    this.editing = true;
  }

  saveEdit(): void {
    if (this.name.invalid || this.description.invalid) {
      return;
    }
    this.communities.update(this.slug, this.name.value.trim(), this.description.value.trim() || null).subscribe({
      next: page => {
        this.page = page;
        this.editing = false;
        this.changes.markForCheck();
      },
      error: () => this.notifications.error('network.communities.saveError')
    });
  }

  /** Its creator (or an administrator) deletes the community, once confirmed; its posts stay as plain posts. */
  deleteCommunity(): void {
    this.confirm.confirm({ messageKey: 'network.communities.deleteConfirm', confirmKey: 'common.delete', danger: true }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.communities.remove(this.slug).subscribe({
        next: () => void this.router.navigate(['/network/communities']),
        error: () => this.notifications.error('network.communities.saveError')
      });
    });
  }
}
