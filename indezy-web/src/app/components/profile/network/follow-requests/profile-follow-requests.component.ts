import { ChangeDetectionStrategy, Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TranslateModule } from '@ngx-translate/core';

import { MemberCard, NetworkMembership } from '../../../../models';
import { NetworkService } from '../../../../services/network/network.service';
import { NotificationService } from '../../../../services/notification/notification.service';

/**
 * Profile → Network: whether the member's card is private (following then asks them, and only their followers read their
 * posts), and the requests to follow waiting for their answer.
 */
@Component({
  selector: 'app-profile-follow-requests',
  imports: [RouterLink, MatButtonModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './profile-follow-requests.component.html',
  styleUrls: ['../profile-network.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ProfileFollowRequestsComponent implements OnInit {
  private readonly network = inject(NetworkService);
  private readonly notifications = inject(NotificationService);

  @Input({ required: true }) membership!: NetworkMembership;
  /** The membership once the card turned private or public. */
  @Output() readonly changed = new EventEmitter<NetworkMembership>();

  requests: MemberCard[] = [];
  saving = false;

  ngOnInit(): void {
    this.network.followRequests().subscribe({
      next: requests => this.requests = requests,
      error: () => this.requests = []
    });
  }

  /** Suggests members found among the workspace's contacts (and one to them), or no longer. */
  setContactSuggestions(on: boolean): void {
    this.saving = true;
    this.network.setContactSuggestions(on).subscribe({
      next: membership => {
        this.saving = false;
        this.changed.emit(membership);
        this.notifications.success('profile.privacy.saved');
      },
      error: () => {
        this.saving = false;
        this.notifications.error('profile.network.saveError');
      }
    });
  }

  /** Suggests members who went through the same ESNs, or no longer. */
  setEsnSuggestions(on: boolean): void {
    this.saving = true;
    this.network.setEsnSuggestions(on).subscribe({
      next: membership => {
        this.saving = false;
        this.changed.emit(membership);
        this.notifications.success(on ? 'profile.network.esnOn' : 'profile.network.esnOff');
      },
      error: () => {
        this.saving = false;
        this.notifications.error('profile.network.saveError');
      }
    });
  }

  /** Turns the card private, or public again: the requests waiting are then approved. */
  setPrivate(privateCard: boolean): void {
    this.saving = true;
    this.network.setPrivate(privateCard).subscribe({
      next: membership => {
        this.saving = false;
        if (!privateCard) {
          this.requests = [];
        }
        this.changed.emit(membership);
        this.notifications.success(privateCard ? 'profile.network.privateOn' : 'profile.network.privateOff');
      },
      error: () => {
        this.saving = false;
        this.notifications.error('profile.network.saveError');
      }
    });
  }

  answer(member: MemberCard, approve: boolean): void {
    this.network.answerFollowRequest(member.handle, approve).subscribe({
      next: requests => {
        this.requests = requests;
        this.notifications.success(approve ? 'profile.network.requestApproved' : 'profile.network.requestDeclined');
      },
      error: () => this.notifications.error('profile.network.saveError')
    });
  }
}
