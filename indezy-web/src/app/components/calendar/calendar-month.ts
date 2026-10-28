import { Booking, InterviewStepDto, StepStatus } from '../../models';
import { fromIsoDate, toIsoDate } from '../../shared/locale/app-locale';

/** One day of the month grid: its date, whether it belongs to the shown month, its dated steps and booked calls by time. */
export interface CalendarDay {
  date: Date;
  key: string;
  inMonth: boolean;
  steps: InterviewStepDto[];
  bookings: Booking[];
}

/** A planned step or a booked call still to come, with its time to order the list by. */
export interface UpcomingItem {
  key: string;
  time: number;
  step?: InterviewStepDto;
  booking?: Booking;
}

/**
 * The weeks, Monday first, that show the month of {@code month} (any day of it), each step placed on the day of its
 * local date and time.
 */
export function monthGrid(month: Date, steps: InterviewStepDto[], bookings: Booking[] = []): CalendarDay[][] {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const start = new Date(first);
  start.setDate(first.getDate() - ((first.getDay() + 6) % 7));
  const byDay = new Map<string, InterviewStepDto[]>();
  for (const step of [...steps].filter(step => !!step.date).sort((a, b) => a.date!.localeCompare(b.date!))) {
    const key = step.date!.substring(0, 10);
    byDay.set(key, [...(byDay.get(key) ?? []), step]);
  }
  // A call is on the day of its start in the browser's time zone; a cancelled one is gone from the calendar.
  const callsByDay = new Map<string, Booking[]>();
  for (const booking of bookings.filter(item => !item.cancelledAt).sort((a, b) => a.startsAt.localeCompare(b.startsAt))) {
    const key = toIsoDate(new Date(booking.startsAt))!;
    callsByDay.set(key, [...(callsByDay.get(key) ?? []), booking]);
  }
  const weeks: CalendarDay[][] = [];
  const day = new Date(start);
  do {
    const week: CalendarDay[] = [];
    for (let i = 0; i < 7; i++) {
      const key = toIsoDate(day)!;
      week.push({ date: new Date(day), key, inMonth: day.getMonth() === first.getMonth(), steps: byDay.get(key) ?? [],
        bookings: callsByDay.get(key) ?? [] });
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

/** The planned steps and the calls not cancelled from {@code now} on, soonest first. */
export function upcoming(steps: InterviewStepDto[], bookings: Booking[], now: Date = new Date()): UpcomingItem[] {
  const items: UpcomingItem[] = [
    ...steps.filter(step => step.status === StepStatus.PLANNED && !!step.date)
      .map(step => ({ key: `step-${step.id}`, time: new Date(step.date!).getTime(), step })),
    ...bookings.filter(booking => !booking.cancelledAt)
      .map(booking => ({ key: `call-${booking.id}`, time: new Date(booking.startsAt).getTime(), booking }))
  ];
  return items.filter(item => item.time >= now.getTime()).sort((a, b) => a.time - b.time);
}
