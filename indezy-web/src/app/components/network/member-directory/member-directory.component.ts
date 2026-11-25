import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NgTemplateOutlet } from '@angular/common';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, forkJoin, map, switchMap } from 'rxjs';

import { MemberCard, MemberDirectory, NetworkAvailability } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NetworkMessagesService } from '../../../services/network/network-messages.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent, memberGradient } from '../avatar/member-avatar.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { NetworkSideComponent } from '../side/network-side.component';
import { PageHeaderComponent } from '../../../shared/components/page-header/page-header.component';
import { TRANSLATED_PAGINATOR } from '../../../shared/i18n/translated-paginator-intl';

type Availability = Exclude<NetworkAvailability, 'AUTO'>;

/** How many skills a member's tile lists before "+n". */
const SKILLS_SHOWN = 3;

/**
 * The tabs of the members page: every member (the search), the members the reader follows, their followers, the requests
 * to follow them waiting for their answer, and the requests they sent.
 */
export type MembersTab = 'all' | 'following' | 'followers' | 'received' | 'sent';
export const MEMBERS_TABS: MembersTab[] = ['all', 'following', 'followers', 'received', 'sent'];

/**
 * The members page (`/network/members`, its tab in `?tab=`): the directory, a search by skill and city with chips for the
 * availability and remote work, the members found as a grid of tiles, each with **Follow**; then the reader's follows,
 * followers and requests, Instagram-like: a follow goes one way, accepted at once or, for a private card, once approved,
 * and opens messages between the two.
 */
@Component({
  providers: [TRANSLATED_PAGINATOR],
  selector: 'app-member-directory',
  imports: [NetworkNavComponent, PageHeaderComponent, ReactiveFormsModule, RouterLink, NgTemplateOutlet, MatButtonModule, MatIconModule, MatTooltipModule, MatPaginatorModule, TranslateModule,
    MemberAvatarComponent, NetworkMeCardComponent, NetworkSideComponent],
  templateUrl: './member-directory.component.html',
  styleUrls: ['./member-directory.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MemberDirectoryComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly messages = inject(NetworkMessagesService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly availabilities: Availability[] = ['AVAILABLE', 'ON_MISSION', 'LISTENING'];
  readonly remotes: (boolean | null)[] = [null, true, false];
  readonly form = inject(FormBuilder).nonNullable.group({
    skill: '',
    city: '',
    availability: null as Availability | null,
    remote: null as boolean | null,
    now: false
  });
  directory: MemberDirectory | null = null;
  notMember = false;
  loading = false;
  /** The handles whose follow, request answer or conversation is on its way. */
  readonly pending = new Set<string>();
  readonly tabs = MEMBERS_TABS;
  tab: MembersTab = 'all';
  /** The members of the tab open but the directory; null while they load. */
  list: MemberCard[] | null = null;
  /** The requests to follow the reader waiting for an answer, counted on their tab. */
  received: number | null = null;
  /** Who the reader follows and asked to follow, as known from the tab read last and the reader's changes since. */
  private readonly followed = new Set<string>();
  private readonly requested = new Set<string>();
  /** The members known to follow the reader: those listed under Followers. */
  private readonly followers = new Set<string>();
  private readonly privateCards = new Set<string>();

  ngOnInit(): void {
    const asked = this.route.snapshot.queryParamMap.get('tab') as MembersTab | null;
    // A skill searched from the network's tabs.
    const skill = this.route.snapshot.queryParamMap.get('skill');
    if (skill) {
      this.form.controls.skill.setValue(skill.slice(0, 60));
    }
    this.tab = asked && MEMBERS_TABS.includes(asked) ? asked : 'all';
    if (this.tab === 'all') {
      this.search();
    } else {
      this.load();
    }
    this.countReceived();
  }

  /** Opens a tab, kept in the page's address. */
  show(tab: MembersTab): void {
    if (tab === this.tab) {
      return;
    }
    this.tab = tab;
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tab: tab === 'all' ? null : tab }, replaceUrl: true });
    if (tab === 'all') {
      this.search(this.directory?.page ?? 0);
    } else {
      this.load();
    }
  }

  /** Reads the members of the tab open: the reader's follows, followers or requests. */
  private load(): void {
    const tab = this.tab;
    this.list = null;
    this.loading = true;
    this.read(tab).subscribe({
      next: members => {
        if (tab !== this.tab) {
          return;
        }
        this.list = members;
        this.loading = false;
        if (tab === 'received') {
          this.received = members.length;
        }
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.list = [];
        this.notMember = error.status === 409;
        if (!this.notMember) {
          this.notifications.error('network.directory.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  private read(tab: MembersTab): Observable<MemberCard[]> {
    switch (tab) {
      case 'received':
        return this.network.followRequests();
      case 'sent':
        return this.network.sentFollowRequests().pipe(map(members => {
          members.forEach(member => this.requested.add(member.handle));
          return members;
        }));
      default:
        return this.network.membership().pipe(
          switchMap(membership => forkJoin([this.network.following(membership.handle ?? ''), this.network.followers(membership.handle ?? '')])),
          map(([following, followers]) => {
            following.forEach(member => this.followed.add(member.handle));
            followers.forEach(member => this.followers.add(member.handle));
            return tab === 'following' ? following : followers;
          }));
    }
  }

  private countReceived(): void {
    this.network.followRequests(true).subscribe({
      next: members => {
        this.received = members.length;
        this.changes.markForCheck();
      },
      error: () => this.received = null
    });
  }

  search(page = 0): void {
    this.loading = true;
    this.network.directory({ ...this.form.getRawValue(), page }).subscribe({
      next: directory => {
        this.directory = directory;
        directory.followed.forEach(handle => this.followed.add(handle));
        directory.requested.forEach(handle => this.requested.add(handle));
        directory.privateCards.forEach(handle => this.privateCards.add(handle));
        this.loading = false;
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.notMember = error.status === 409;
        if (!this.notMember) {
          this.notifications.error('network.directory.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  onPage(event: PageEvent): void {
    this.search(event.pageIndex);
  }

  /** Picks an availability chip (null for any) and searches again. */
  pickAvailability(availability: Availability | null): void {
    this.form.controls.availability.setValue(availability);
    this.search();
  }

  /** Shows the members **Open to work** alone, or everyone again. */
  toggleNow(): void {
    this.form.controls.now.setValue(!this.form.controls.now.value);
    this.search();
  }

  /** Picks a remote-work chip (null for either) and searches again. */
  pickRemote(remote: boolean | null): void {
    this.form.controls.remote.setValue(remote);
    this.search();
  }

  remoteLabel(remote: boolean | null): string {
    return remote === null ? 'network.directory.remoteAny' : remote ? 'network.directory.remoteYes' : 'network.directory.remoteNo';
  }

  cover(handle: string): string {
    return memberGradient(handle, 120);
  }

  skills(member: MemberCard): string[] {
    return member.stack.slice(0, SKILLS_SHOWN);
  }

  moreSkills(member: MemberCard): number {
    return Math.max(0, member.stack.length - SKILLS_SHOWN);
  }

  isFollowed(handle: string): boolean {
    return this.followed.has(handle);
  }

  isRequested(handle: string): boolean {
    return this.requested.has(handle);
  }

  /** Messages are open once either follows the other. */
  canMessage(handle: string): boolean {
    return this.followed.has(handle) || this.followers.has(handle);
  }

  /** Following, Request sent, Ask to follow a private card, or Follow. */
  followLabel(handle: string): string {
    if (this.isFollowed(handle)) {
      return 'network.directory.following';
    }
    if (this.isRequested(handle)) {
      return 'network.page.requested';
    }
    return this.privateCards.has(handle) ? 'network.page.askToFollow' : 'network.page.follow';
  }

  /** Follows the member (or asks to), or undoes the follow or the request. */
  toggleFollow(handle: string): void {
    const undo = this.isFollowed(handle) || this.isRequested(handle);
    this.pending.add(handle);
    (undo ? this.network.unfollow(handle) : this.network.follow(handle)).subscribe({
      next: page => {
        this.pending.delete(handle);
        this.mark(this.followed, handle, page.followed);
        this.mark(this.requested, handle, page.requested);
        if (page.privateCard) {
          this.privateCards.add(handle);
        }
        const directory = this.directory;
        if (directory) {
          const without = (handles: string[]): string[] => handles.filter(item => item !== handle);
          this.directory = { ...directory, followed: page.followed ? [...without(directory.followed), handle] : without(directory.followed),
            requested: page.requested ? [...without(directory.requested), handle] : without(directory.requested) };
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

  /** Approves a member's request to follow, who then follows the reader, or declines it; the tab shows the requests left. */
  answer(handle: string, approve: boolean): void {
    this.pending.add(handle);
    this.network.answerFollowRequest(handle, approve).subscribe({
      next: left => {
        this.pending.delete(handle);
        if (approve) {
          this.followers.add(handle);
        }
        this.list = left;
        this.received = left.length;
        this.notifications.success(approve ? 'network.members.approved' : 'network.members.declined');
        this.changes.markForCheck();
      },
      error: () => {
        this.pending.delete(handle);
        this.notifications.error('network.members.answerError');
        this.changes.markForCheck();
      }
    });
  }

  /** Opens the conversation with the member, a new one if none. */
  message(handle: string): void {
    this.pending.add(handle);
    this.messages.open(handle).subscribe({
      next: conversation => {
        this.pending.delete(handle);
        void this.router.navigate(['/network/messages', conversation.id]);
      },
      error: () => {
        this.pending.delete(handle);
        this.notifications.error('network.messages.openError');
        this.changes.markForCheck();
      }
    });
  }

  private mark(handles: Set<string>, handle: string, on: boolean): void {
    if (on) {
      handles.add(handle);
    } else {
      handles.delete(handle);
    }
  }
}
