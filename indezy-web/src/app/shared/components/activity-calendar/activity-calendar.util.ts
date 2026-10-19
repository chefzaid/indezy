import { ActivityReportDay, ActivityDayType, PublicHoliday } from '../../../models/activity-report.models';

/** What tapping a day records: a full or half day on site, remote or absent, or nothing. */
export type DayBrush = 'ONSITE' | 'REMOTE' | 'HALF_ONSITE' | 'HALF_REMOTE' | 'ABSENCE' | 'HALF_ABSENCE' | 'CLEAR';

export const DAY_BRUSHES: DayBrush[] = ['ONSITE', 'REMOTE', 'HALF_ONSITE', 'HALF_REMOTE', 'ABSENCE', 'HALF_ABSENCE', 'CLEAR'];

const BRUSH_ENTRIES: Record<Exclude<DayBrush, 'CLEAR'>, { type: ActivityDayType; halfDay: boolean }> = {
  ONSITE: { type: 'ONSITE', halfDay: false },
  REMOTE: { type: 'REMOTE', halfDay: false },
  HALF_ONSITE: { type: 'ONSITE', halfDay: true },
  HALF_REMOTE: { type: 'REMOTE', halfDay: true },
  ABSENCE: { type: 'ABSENCE', halfDay: false },
  HALF_ABSENCE: { type: 'ABSENCE', halfDay: true }
};

export interface ActivityTotals {
  worked: number;
  onsite: number;
  remote: number;
  absence: number;
}

/** The ISO dates (yyyy-MM-dd) of a month, month being 1 to 12. */
export function monthDates(year: number, month: number): string[] {
  const length = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return Array.from({ length }, (_, index) => isoDate(year, month, index + 1));
}

/** The month as weeks starting on Monday, with null for the days of the neighbouring months. */
export function monthWeeks(year: number, month: number): (string | null)[][] {
  const dates = monthDates(year, month);
  const leading = (weekday(dates[0]) + 6) % 7;
  const cells: (string | null)[] = [...Array<null>(leading).fill(null), ...dates];
  while (cells.length % 7 !== 0) {
    cells.push(null);
  }
  return Array.from({ length: cells.length / 7 }, (_, week) => cells.slice(week * 7, week * 7 + 7));
}

export function isWeekend(date: string): boolean {
  const day = weekday(date);
  return day === 0 || day === 6;
}

/** Whether the date falls within the mission, when its dates are known. */
export function isWithinMission(date: string, missionStart?: string, missionEnd?: string): boolean {
  return (!missionStart || date >= missionStart) && (!missionEnd || date <= missionEnd);
}

/** Records the brush on the date; the same brush again, or the clear brush, removes the entry. */
export function applyBrush(days: ActivityReportDay[], date: string, brush: DayBrush): ActivityReportDay[] {
  const others = days.filter(day => day.date !== date);
  if (brush === 'CLEAR') {
    return others;
  }
  const entry = BRUSH_ENTRIES[brush];
  const existing = days.find(day => day.date === date);
  if (existing && existing.type === entry.type && existing.halfDay === entry.halfDay) {
    return others;
  }
  return sortByDate([...others, { date, ...entry }]);
}

/** Records every empty business day of the month within the mission with a full day of the given type. */
export function fillBusinessDays(days: ActivityReportDay[], year: number, month: number, holidays: PublicHoliday[],
                                 type: 'ONSITE' | 'REMOTE', missionStart?: string, missionEnd?: string): ActivityReportDay[] {
  const recorded = new Set(days.map(day => day.date));
  const holidayDates = new Set(holidays.map(holiday => holiday.date));
  const added = monthDates(year, month)
    .filter(date => !recorded.has(date) && !isWeekend(date) && !holidayDates.has(date))
    .filter(date => isWithinMission(date, missionStart, missionEnd))
    .map(date => ({ date, type, halfDay: false }));
  return sortByDate([...days, ...added]);
}

export function totals(days: ActivityReportDay[]): ActivityTotals {
  const duration = (day: ActivityReportDay): number => day.halfDay ? 0.5 : 1;
  const sum = (filter: (day: ActivityReportDay) => boolean): number =>
    days.filter(filter).reduce((total, day) => total + duration(day), 0);
  return {
    worked: sum(day => day.type !== 'ABSENCE'),
    onsite: sum(day => day.type === 'ONSITE'),
    remote: sum(day => day.type === 'REMOTE'),
    absence: sum(day => day.type === 'ABSENCE')
  };
}

/** The brush matching a recorded day, to describe it. */
export function brushOf(day: ActivityReportDay): Exclude<DayBrush, 'CLEAR'> {
  const prefix = day.halfDay ? 'HALF_' : '';
  return `${prefix}${day.type}` as Exclude<DayBrush, 'CLEAR'>;
}

function sortByDate(days: ActivityReportDay[]): ActivityReportDay[] {
  return [...days].sort((first, second) => first.date.localeCompare(second.date));
}

function isoDate(year: number, month: number, day: number): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

/** Day of the week of an ISO date, 0 for Sunday, independent of the browser's time zone. */
function weekday(date: string): number {
  const [year, month, day] = date.split('-').map(Number);
  return new Date(Date.UTC(year, month - 1, day)).getUTCDay();
}
