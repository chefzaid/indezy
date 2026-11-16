import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard, MemberDirectory, NetworkAvailability } from '../../../models';
import { NetworkService } from '../../../services/network/network.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent, memberGradient } from '../avatar/member-avatar.component';

type Availability = Exclude<NetworkAvailability, 'AUTO'>;

/** How many skills a member's tile lists before "+n". */
const SKILLS_SHOWN = 3;

/**
 * The member directory (`/network/members`): a search by skill and city, chips for the availability and remote work,
 * and the members found as a grid of tiles, each with **Follow**.
 */
@Component({
  selector: 'app-member-directory',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatIconModule, MatPaginatorModule, TranslateModule, MemberAvatarComponent],
  templateUrl: './member-directory.component.html',
  styleUrls: ['./member-directory.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MemberDirectoryComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly availabilities: Availability[] = ['AVAILABLE', 'ON_MISSION', 'LISTENING'];
  readonly remotes: (boolean | null)[] = [null, true, false];
  readonly form = inject(FormBuilder).nonNullable.group({
    skill: '',
    city: '',
    availability: null as Availability | null,
    remote: null as boolean | null
  });
  directory: MemberDirectory | null = null;
  notMember = false;
  loading = false;
  /** The handles whose follow is on its way. */
  readonly pending = new Set<string>();

  ngOnInit(): void {
    this.search();
  }

  search(page = 0): void {
    this.loading = true;
    this.network.directory({ ...this.form.getRawValue(), page }).subscribe({
      next: directory => {
        this.directory = directory;
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
    return this.directory?.followed.includes(handle) ?? false;
  }

  isRequested(handle: string): boolean {
    return this.directory?.requested.includes(handle) ?? false;
  }

  /** Following, Request sent, Ask to follow a private card, or Follow. */
  followLabel(handle: string): string {
    if (this.isFollowed(handle)) {
      return 'network.directory.following';
    }
    if (this.isRequested(handle)) {
      return 'network.page.requested';
    }
    return this.directory?.privateCards.includes(handle) ? 'network.page.askToFollow' : 'network.page.follow';
  }

  /** Follows the member (or asks to), or undoes the follow or the request. */
  toggleFollow(handle: string): void {
    const undo = this.isFollowed(handle) || this.isRequested(handle);
    this.pending.add(handle);
    (undo ? this.network.unfollow(handle) : this.network.follow(handle)).subscribe({
      next: page => {
        this.pending.delete(handle);
        const directory = this.directory!;
        const without = (handles: string[]): string[] => handles.filter(item => item !== handle);
        this.directory = { ...directory, followed: page.followed ? [...without(directory.followed), handle] : without(directory.followed),
          requested: page.requested ? [...without(directory.requested), handle] : without(directory.requested) };
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
