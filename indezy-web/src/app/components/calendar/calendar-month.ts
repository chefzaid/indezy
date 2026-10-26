import { InterviewStepDto } from '../../models';
import { fromIsoDate, toIsoDate } from '../../shared/locale/app-locale';

/** One day of the month grid: its date, whether it belongs to the shown month, and its dated steps by time. */
export interface CalendarDay {
  date: Date;
  key: string;
  inMonth: boolean;
  steps: InterviewStepDto[];
}

/**
 * The weeks, Monday first, that show the month of {@code month} (any day of it), each step placed on the day of its
 * local date and time.
 */
export function monthGrid(month: Date, steps: InterviewStepDto[]): CalendarDay[][] {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const start = new Date(first);
  start.setDate(first.getDate() - ((first.getDay() + 6) % 7));
  const byDay = new Map<string, InterviewStepDto[]>();
  for (const step of [...steps].filter(step => !!step.date).sort((a, b) => a.date!.localeCompare(b.date!))) {
    const key = step.date!.substring(0, 10);
    byDay.set(key, [...(byDay.get(key) ?? []), step]);
  }
  const weeks: CalendarDay[][] = [];
  const day = new Date(start);
  do {
    const week: CalendarDay[] = [];
    for (let i = 0; i < 7; i++) {
      const key = toIsoDate(day)!;
      week.push({ date: new Date(day), key, inMonth: day.getMonth() === first.getMonth(), steps: byDay.get(key) ?? [] });
      day.setDate(day.getDate() + 1);
    }
    weeks.push(week);
  } while (day.getMonth() === first.getMonth());
  return weeks;
}

/** The month a page address names (yyyy-MM), or the current one. */
export function monthOf(value: string | null, today: Date = new Date()): Date {
  const parsed = value && /^\d{4}-\d{2}$/.test(value) ? fromIsoDate(`${value}-01`) : null;
  return parsed ?? new Date(today.getFullYear(), today.getMonth(), 1);
}

/** The page address of a month: yyyy-MM. */
export function monthKey(month: Date): string {
  return toIsoDate(month)!.substring(0, 7);
}
