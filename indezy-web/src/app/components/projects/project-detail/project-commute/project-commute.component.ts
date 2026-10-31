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
/** Working days in a month, over which the days on site are spread. */
const WORKING_DAYS_PER_MONTH = 20;
/** A working day's hours, which price an hour of the daily rate. */
const HOURS_PER_DAY = 7;

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
  /** The mission's daily rate, which its effective rate starts from. */
  @Input() dailyRate: number | null | undefined = null;

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

  /**
   * What the mission pays a day once its commute is counted: the hours there and back at rush hour priced at the daily
   * rate's hourly rate ({@link HOURS_PER_DAY} hours a day), and the day's cost when the profile sets one, taken off on
   * each day on site and spread over the month's working days. Null without a rate or a time.
   */
  effectiveRate(trip: CommuteInfoDto): number | null {
    const seconds = trip.rushDurationInSeconds ?? trip.durationInSeconds;
    if (!this.dailyRate || seconds === null || seconds === undefined) {
      return null;
    }
    const perDayOnSite = 2 * seconds / 3600 * this.dailyRate / HOURS_PER_DAY + (trip.dailyCost ?? 0);
    return Math.round(this.dailyRate - trip.onsiteDaysPerMonth / WORKING_DAYS_PER_MONTH * perDayOnSite);
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
