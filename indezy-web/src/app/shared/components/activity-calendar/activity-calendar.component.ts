import { ChangeDetectionStrategy, Component, EventEmitter, Input, LOCALE_ID, OnChanges, Output, inject } from '@angular/core';
import { NgTemplateOutlet, formatDate } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ActivityReportDay, PublicHoliday } from '../../../models/activity-report.models';
import { fromIsoDate } from '../../locale/app-locale';
import { brushOf, isWeekend, isWithinMission, monthWeeks } from './activity-calendar.util';

/** One day of the month as shown: what was recorded, and why it is not a business day. */
interface CalendarCell {
  date: string;
  dayNumber: number;
  /** Translation key of the recorded state, or undefined when nothing was recorded. */
  stateKey?: string;
  icon?: string;
  halfDay: boolean;
  type?: string;
  holidayKey?: string;
  off: boolean;
  outsideMission: boolean;
  description: string;
}

const TYPE_ICONS: Record<string, string> = { ONSITE: 'business', REMOTE: 'home', ABSENCE: 'event_busy' };

/**
 * A month of an activity report, Monday first: recorded days with their location or absence, weekends and public
 * holidays shaded, days outside the mission dimmed. When editable, tapping a day emits it.
 */
@Component({
  selector: 'app-activity-calendar',
  imports: [NgTemplateOutlet, MatIconModule, TranslateModule],
  templateUrl: './activity-calendar.component.html',
  styleUrls: ['./activity-calendar.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ActivityCalendarComponent implements OnChanges {
  @Input({ required: true }) year!: number;
  @Input({ required: true }) month!: number;
  @Input() days: ActivityReportDay[] = [];
  @Input() holidays: PublicHoliday[] = [];
  @Input() missionStart?: string;
  @Input() missionEnd?: string;
  @Input() editable = false;
  @Output() readonly dayToggled = new EventEmitter<string>();

  private readonly locale = inject(LOCALE_ID);
  private readonly translate = inject(TranslateService);

  weeks: (CalendarCell | null)[][] = [];
  weekdayNames: string[] = [];

  ngOnChanges(): void {
    // 2024-01-01 was a Monday.
    this.weekdayNames = Array.from({ length: 7 }, (_, index) => formatDate(new Date(2024, 0, 1 + index), 'EEE', this.locale));
    const recorded = new Map(this.days.map(day => [day.date, day]));
    const holidays = new Map(this.holidays.map(holiday => [holiday.date, holiday.holiday]));
    this.weeks = monthWeeks(this.year, this.month)
      .map(week => week.map(date => date ? this.cell(date, recorded.get(date), holidays.get(date)) : null));
  }

  toggle(cell: CalendarCell): void {
    if (this.editable) {
      this.dayToggled.emit(cell.date);
    }
  }

  private cell(date: string, day: ActivityReportDay | undefined, holiday: string | undefined): CalendarCell {
    const stateKey = day ? `activityReports.brushes.${brushOf(day)}` : undefined;
    const holidayKey = holiday ? `activityReports.holidays.${holiday}` : undefined;
    const outsideMission = !isWithinMission(date, this.missionStart, this.missionEnd);
    const details = [stateKey ?? 'activityReports.calendar.notWorked', holidayKey,
      !holiday && isWeekend(date) ? 'activityReports.calendar.weekend' : undefined,
      outsideMission ? 'activityReports.calendar.outsideMission' : undefined]
      .filter((key): key is string => !!key)
      .map(key => this.translate.instant(key));
    return {
      date,
      dayNumber: Number(date.substring(8)),
      stateKey,
      icon: day ? TYPE_ICONS[day.type] : undefined,
      halfDay: !!day?.halfDay,
      type: day?.type,
      holidayKey,
      off: isWeekend(date) || !!holiday,
      outsideMission,
      description: `${formatDate(fromIsoDate(date)!, 'fullDate', this.locale)} : ${details.join(', ')}`
    };
  }
}
