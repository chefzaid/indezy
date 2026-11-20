import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { MeetupAttendance, NetworkMeetup } from '../../../models';
import { NetworkMeetupsService } from '../../../services/network/network-meetups.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';

/**
 * An event as the lists show it: its date, title, time and place (or Online), its tags, who organizes it, how many go,
 * and **Going** / **Interested**, answered at once.
 */
@Component({
  selector: 'app-event-card',
  imports: [DatePipe, RouterLink, MatButtonModule, MatIconModule, TranslateModule, MemberAvatarComponent],
  templateUrl: './event-card.component.html',
  styleUrls: ['./event-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class EventCardComponent {
  private readonly meetups = inject(NetworkMeetupsService);
  private readonly notifications = inject(NotificationService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) meetup!: NetworkMeetup;
  /** A smaller card, for a side column: no tags nor answers. */
  @Input() compact = false;
  @Output() readonly changed = new EventEmitter<NetworkMeetup>();
  answering = false;

  /** Answers Going or Interested, or takes the answer back when it is the one given. */
  answer(status: MeetupAttendance): void {
    if (this.answering) {
      return;
    }
    this.answering = true;
    this.meetups.attend(this.meetup.id, this.meetup.mine === status ? null : status).subscribe({
      next: meetup => {
        this.answering = false;
        this.meetup = meetup;
        this.changed.emit(meetup);
        this.changes.markForCheck();
      },
      error: () => {
        this.answering = false;
        this.notifications.error('network.events.saveError');
        this.changes.markForCheck();
      }
    });
  }
}
