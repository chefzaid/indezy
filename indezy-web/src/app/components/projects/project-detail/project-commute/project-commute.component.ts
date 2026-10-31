import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { CommuteInfoDto } from '../../../../models/commute.models';
import { CommuteService } from '../../../../services/commute/commute.service';
import { tripTime } from '../../../../shared/utils/trip-time';

/** Weeks in a month of 20 working days: what one remote day a week gives back. */
const WEEKS_PER_MONTH = 4;

/**
 * The commute to an opportunity: one way by car and by public transport, off-peak and at rush hour, the month's time
 * there and back, and the time each remote day a week gives back. Hidden for a fully remote mission.
 */
@Component({
  selector: 'app-project-commute',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DecimalPipe, RouterLink, MatCardModule, MatIconModule, TranslateModule],
  templateUrl: './project-commute.component.html',
  styleUrl: './project-commute.component.scss'
})
export class ProjectCommuteComponent implements OnChanges {
  private readonly commuteService = inject(CommuteService);
  private readonly changes = inject(ChangeDetectorRef);

  @Input({ required: true }) projectId!: number;

  trips: CommuteInfoDto[] = [];

  ngOnChanges(): void {
    this.trips = [];
    this.commuteService.getProjectCommute(this.projectId).subscribe({
      next: trips => {
        this.trips = trips.some(trip => trip.remote) ? [] : trips;
        this.changes.markForCheck();
      },
      error: () => {
        this.trips = [];
        this.changes.markForCheck();
      }
    });
  }

  /** "≈ 50 min", Google's own words, or with the rush hour time when it differs. */
  time(trip: CommuteInfoDto): string {
    const prefix = trip.estimated ? '≈ ' : '';
    const time = trip.durationText ?? tripTime((trip.durationInSeconds ?? 0) / 60);
    return prefix + time;
  }

  rush(trip: CommuteInfoDto): string | null {
    const rush = trip.rushDurationInSeconds;
    return rush === null || rush === undefined || Math.round(rush / 60) === Math.round((trip.durationInSeconds ?? 0) / 60) ? null
      : (trip.estimated ? '≈ ' : '') + tripTime(rush / 60);
  }

  distance(trip: CommuteInfoDto): string {
    return trip.distanceText ?? `${Math.round((trip.distanceInMeters ?? 0) / 1000)} km`;
  }

  /** The month's commute in hours, to the tenth. */
  monthlyHours(trip: CommuteInfoDto): number {
    return Math.round((trip.monthlySeconds ?? 0) / 360) / 10;
  }

  /** Hours a month one remote day a week gives back on the quickest way there, null without a time or a day on site a week. */
  remoteDaySaving(): number | null {
    const times = this.trips.filter(trip => trip.durationInSeconds !== null && trip.durationInSeconds !== undefined)
      .map(trip => trip.rushDurationInSeconds ?? trip.durationInSeconds!);
    if (times.length === 0 || this.trips[0].onsiteDaysPerMonth < WEEKS_PER_MONTH) {
      return null;
    }
    return Math.round(Math.min(...times) * 2 * WEEKS_PER_MONTH / 360) / 10;
  }
}
