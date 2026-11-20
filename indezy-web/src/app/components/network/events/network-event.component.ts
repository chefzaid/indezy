import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { EMPTY, map, switchMap } from 'rxjs';

import { MeetupAttendees, NetworkMeetup, NewMeetup } from '../../../models';
import { NetworkMeetupsService } from '../../../services/network/network-meetups.service';
import { NotificationService } from '../../../services/notification/notification.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { saveBlob } from '../../../shared/utils/download-file';
import { MemberAvatarComponent } from '../avatar/member-avatar.component';
import { NetworkMeCardComponent } from '../me-card/network-me-card.component';
import { NetworkNavComponent } from '../nav/network-nav.component';
import { NetworkSideComponent } from '../side/network-side.component';
import { EventCardComponent } from './event-card.component';
import { EventFormComponent } from './event-form.component';

/**
 * An event's page (`/network/events/:id`): its card, description and link, who goes and who is interested, **Add to my
 * calendar** (an .ics file), and for its organizer (or an administrator) **Edit** and **Cancel the event**.
 */
@Component({
  selector: 'app-network-event',
  imports: [RouterLink, MatButtonModule, MatIconModule, TranslateModule, MemberAvatarComponent, NetworkMeCardComponent, NetworkNavComponent,
    NetworkSideComponent, EventCardComponent, EventFormComponent],
  templateUrl: './network-event.component.html',
  styleUrls: ['./network-event.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class NetworkEventComponent implements OnInit {
  private readonly meetups = inject(NetworkMeetupsService);
  private readonly notifications = inject(NotificationService);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly route = inject(ActivatedRoute);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  meetup: NetworkMeetup | null = null;
  attendees: MeetupAttendees | null = null;
  missing = false;
  editing = false;
  saving = false;

  ngOnInit(): void {
    this.route.paramMap.pipe(map(params => Number(params.get('id'))), takeUntilDestroyed(this.destroyRef)).subscribe(id => this.load(id));
  }

  private load(id: number): void {
    this.meetups.get(id).subscribe({
      next: meetup => {
        this.meetup = meetup;
        this.missing = false;
        this.loadAttendees();
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.missing = error.status === 404;
        if (!this.missing) {
          this.notifications.error('network.events.loadError');
        }
        this.changes.markForCheck();
      }
    });
  }

  private loadAttendees(): void {
    this.meetups.attendees(this.meetup!.id).subscribe({
      next: attendees => {
        this.attendees = attendees;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** An answer given: the counts and who goes follow it. */
  changed(meetup: NetworkMeetup): void {
    this.meetup = meetup;
    this.loadAttendees();
  }

  /** Downloads the event as an .ics file, for any calendar. */
  addToCalendar(): void {
    const meetup = this.meetup!;
    this.meetups.ics(meetup.id).subscribe({
      next: blob => saveBlob(blob, `indezy-event-${meetup.id}.ics`),
      error: () => this.notifications.error('network.events.icsError')
    });
  }

  save(details: NewMeetup): void {
    this.saving = true;
    this.meetups.update(this.meetup!.id, details).subscribe({
      next: meetup => {
        this.saving = false;
        this.editing = false;
        this.meetup = meetup;
        this.notifications.success('network.events.saved');
        this.changes.markForCheck();
      },
      error: (error: HttpErrorResponse) => {
        this.saving = false;
        this.notifications.error(error.status === 400 ? 'network.events.invalid' : 'network.events.saveError');
        this.changes.markForCheck();
      }
    });
  }

  /** Cancels the event once confirmed; those who answered are told. */
  cancel(): void {
    this.confirm.confirm({ messageKey: 'network.events.cancelConfirm', confirmKey: 'network.events.cancelEvent', danger: true })
      .pipe(switchMap(confirmed => confirmed ? this.meetups.cancel(this.meetup!.id) : EMPTY))
      .subscribe({
        next: meetup => {
          this.meetup = meetup;
          this.notifications.success('network.events.cancelledDone');
          this.changes.markForCheck();
        },
        error: () => this.notifications.error('network.events.saveError')
      });
  }
}
