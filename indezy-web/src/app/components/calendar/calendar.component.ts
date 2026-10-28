import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';

import { Booking, InterviewStepDto, STEP_STATUS_COLORS } from '../../models';
import { BookingService } from '../../services/booking/booking.service';
import { BookingLinkComponent } from './booking-link/booking-link.component';
import { AuthService } from '../../services/auth/auth.service';
import { InterviewStepService } from '../../services/interview-step/interview-step.service';
import { NotificationService } from '../../services/notification/notification.service';
import { toIsoDate } from '../../shared/locale/app-locale';
import { CalendarDay, UpcomingItem, monthGrid, monthKey, monthOf, upcoming } from './calendar-month';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';

/** The calendar's two views: the month grid, or the list of what is still to come. */
export type CalendarView = 'month' | 'upcoming';

/**
 * A month of every dated interview step across the opportunities, Monday first: each day lists its steps by time,
 * coloured by status, and a picked day lists them in full below the grid. The month is in the address (?month=yyyy-MM).
 * The upcoming view (?view=upcoming) lists the planned steps and booked calls to come, soonest first.
 */
@Component({
  selector: 'app-calendar',
  imports: [PageHeaderComponent, DatePipe, RouterModule, MatButtonModule, MatButtonToggleModule, MatIconModule, MatTooltipModule, TranslateModule,
    BookingLinkComponent],
  templateUrl: './calendar.component.html',
  styleUrls: ['./calendar.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CalendarComponent implements OnInit {
  readonly statusColors = STEP_STATUS_COLORS;
  /** Steps a day shows before "+n" on wide screens. */
  readonly perDay = 3;
  month = monthOf(null);
  view: CalendarView = 'month';
  weeks: CalendarDay[][] = [];
  upcomingItems: UpcomingItem[] = [];
  selected: CalendarDay | null = null;
  readonly todayKey = toIsoDate(new Date())!;
  private steps: InterviewStepDto[] = [];
  private bookings: Booking[] = [];

  private readonly stepService = inject(InterviewStepService);
  private readonly bookingService = inject(BookingService);
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.month = monthOf(params.get('month'));
      this.view = params.get('view') === 'upcoming' ? 'upcoming' : 'month';
      this.layout();
    });
    const freelanceId = this.authService.getUser()?.id;
    if (!freelanceId) {
      return;
    }
    this.stepService.getByFreelanceIdAndStatus(freelanceId).subscribe({
      next: steps => {
        this.steps = steps;
        this.layout();
      },
      error: () => this.notificationService.error('calendar.loadError')
    });
    this.loadBookings();
  }

  /** The calls booked through the link, shown with the steps; read again when one is cancelled. */
  loadBookings(): void {
    this.bookingService.list().subscribe({
      next: bookings => {
        this.bookings = bookings;
        this.layout();
      },
      error: () => this.notificationService.error('calendar.loadError')
    });
  }

  /** Shows the month {@code offset} months away from the shown one, or the current month without offset. */
  go(offset?: number): void {
    const month = offset === undefined ? monthOf(null) : new Date(this.month.getFullYear(), this.month.getMonth() + offset, 1);
    this.router.navigate([], { relativeTo: this.route, queryParams: { month: monthKey(month) } });
  }

  /** Switches between the month grid and the upcoming list, kept in the address with the month. */
  show(view: CalendarView): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { view: view === 'upcoming' ? view : null },
      queryParamsHandling: 'merge' });
  }

  /** The calls that fit in a day's cell after its steps, which come first. */
  callsShown(day: CalendarDay): Booking[] {
    return day.bookings.slice(0, Math.max(0, this.perDay - day.steps.length));
  }

  select(day: CalendarDay): void {
    this.selected = day;
  }

  private layout(): void {
    this.weeks = monthGrid(this.month, this.steps, this.bookings);
    this.upcomingItems = upcoming(this.steps, this.bookings);
    const days = this.weeks.flat();
    const kept = this.selected && days.find(day => day.key === this.selected!.key && day.inMonth);
    this.selected = kept ?? days.find(day => day.key === this.todayKey && day.inMonth) ?? days.find(day => day.inMonth) ?? null;
    this.changes.markForCheck();
  }
}
