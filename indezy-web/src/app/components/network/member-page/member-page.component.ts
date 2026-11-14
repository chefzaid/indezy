import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, map, switchMap } from 'rxjs';

import { MemberCard, MemberPage } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberCardComponent } from '../member-card/member-card.component';

/** Which list of the page is open. */
export type MemberList = 'followers' | 'following';

/**
 * A member's page (`/m/<handle>`): the card, the members following them and they follow, the skills they share with
 * the viewer, and **Follow**; on the viewer's own page, removing a follower.
 */
@Component({
  selector: 'app-member-page',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatProgressSpinnerModule, TranslateModule, MemberCardComponent],
  templateUrl: './member-page.component.html',
  styleUrls: ['./member-page.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MemberPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  page: MemberPage | null = null;
  /** Why the page cannot be shown: the viewer is not a member yet, or no member holds the handle. */
  problem: 'notMember' | 'notFound' | 'error' | null = null;
  loading = true;
  list: MemberList | null = null;
  members: MemberCard[] = [];
  isFollowing = false;

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
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.problem = error.status === 409 ? 'notMember' : error.status === 404 ? 'notFound' : 'error';
        this.changes.markForCheck();
      }
    });
  }

  toggleFollow(): void {
    const page = this.page!;
    const request: Observable<MemberPage> = page.followed ? this.network.unfollow(page.card.handle) : this.network.follow(page.card.handle);
    this.isFollowing = true;
    request.subscribe({
      next: updated => {
        this.page = updated;
        this.isFollowing = false;
        if (this.list === 'followers') {
          this.show('followers');
        }
      },
      error: () => {
        this.isFollowing = false;
        this.notifications.error('network.page.followError');
      }
    });
  }

  /** Opens a list, or closes it when open. */
  show(list: MemberList): void {
    const handle = this.page!.card.handle;
    this.list = list;
    (list === 'followers' ? this.network.followers(handle) : this.network.following(handle)).subscribe({
      next: members => this.members = members,
      error: () => this.notifications.error('network.page.listError')
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
}
