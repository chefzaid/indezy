import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * How long ago a moment was, as social feeds say it: "now", "5 min ago", "3 h ago", "2 days ago"; past a week, the date
 * ("12 Nov"), with its year when not this one. In the interface language.
 */
@Pipe({ name: 'timeAgo' })
export class TimeAgoPipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(value: string | Date | null | undefined, now: Date = new Date()): string {
    if (!value) {
      return '';
    }
    const at = value instanceof Date ? value : new Date(value);
    const locale = this.translate.currentLang || this.translate.getDefaultLang() || 'fr';
    const elapsed = now.getTime() - at.getTime();
    if (elapsed < MINUTE) {
      return this.translate.instant('network.time.now');
    }
    const relative = new Intl.RelativeTimeFormat(locale, { numeric: 'always', style: 'short' });
    if (elapsed < HOUR) {
      return relative.format(-Math.floor(elapsed / MINUTE), 'minute');
    }
    if (elapsed < DAY) {
      return relative.format(-Math.floor(elapsed / HOUR), 'hour');
    }
    if (elapsed < 7 * DAY) {
      return relative.format(-Math.floor(elapsed / DAY), 'day');
    }
    return new Intl.DateTimeFormat(locale, at.getFullYear() === now.getFullYear() ? { day: 'numeric', month: 'short' } :
      { day: 'numeric', month: 'short', year: 'numeric' }).format(at);
  }
}
