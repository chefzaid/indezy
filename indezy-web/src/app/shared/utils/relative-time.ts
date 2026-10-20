const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600],
  ['month', 30 * 24 * 3600],
  ['week', 7 * 24 * 3600],
  ['day', 24 * 3600],
  ['hour', 3600],
  ['minute', 60]
];

/**
 * How long ago (or in how long) a moment is, in words of the locale: "il y a 2 heures", "yesterday",
 * "now". Under a minute reads as now.
 */
export function relativeTime(moment: string | Date, locale: string, now: Date = new Date()): string {
  const seconds = Math.round((new Date(moment).getTime() - now.getTime()) / 1000);
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  for (const [unit, length] of UNITS) {
    if (Math.abs(seconds) >= length) {
      return format.format(Math.trunc(seconds / length), unit);
    }
  }
  return format.format(0, 'second');
}

/**
 * How many days ago (or in how many days) a calendar day (yyyy-MM-dd) is, in words of the locale: "today",
 * "il y a 3 jours", "last week". Days are counted on the local calendar, whatever the time of day.
 */
export function relativeDay(day: string, locale: string, now: Date = new Date()): string {
  const [year, month, date] = day.split('-').map(Number);
  const days = Math.round((Date.UTC(year, month - 1, date) - Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())) / 86_400_000);
  const format = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  if (Math.abs(days) >= 30) {
    return format.format(Math.trunc(days / 30), 'month');
  }
  return Math.abs(days) >= 7 ? format.format(Math.trunc(days / 7), 'week') : format.format(days, 'day');
}
