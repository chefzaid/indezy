import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, Input, OnChanges, ViewChild, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { A11yModule } from '@angular/cdk/a11y';
import { OverlayModule, ConnectedPosition } from '@angular/cdk/overlay';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ActivityDay, ActivityItem } from '../../../models';
import { AppLanguage, getAppLocale, getSavedLanguage, toIsoDate } from '../../locale/app-locale';

/** A day of the heatmap: its date, how much happened and what. */
export interface HeatmapCell {
  date: string;
  count: number;
  opportunities: number;
  interviews: number;
  items: ActivityItem[];
  /** Its column (week) and row (0 for Monday). */
  week: number;
  weekday: number;
}

/** A month's name above the week it starts in. */
export interface MonthLabel {
  week: number;
  name: string;
}

/**
 * GitHub-style activity heatmap: a column per week (Monday to Sunday) over the last year, each day shaded by how many
 * opportunities were created and interview steps held, with the months above and the weekdays beside. A day grows on
 * hover or focus and tells what happened (the date, the counts, the first opportunities and steps); a click or Enter
 * keeps its card open, its opportunities a click away. The arrows move between days, as in a calendar.
 */
@Component({
  selector: 'app-activity-heatmap',
  imports: [TranslateModule, DatePipe, RouterLink, OverlayModule, A11yModule, MatIconModule],
  templateUrl: './activity-heatmap.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./activity-heatmap.component.scss']
})
export class ActivityHeatmapComponent implements OnChanges {
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  @Input() activity: ActivityDay[] = [];
  /** The last day shown; today by default. */
  @Input() today: Date | null = null;
  @ViewChild('scroller') scroller?: ElementRef<HTMLElement>;

  weeks: HeatmapCell[][] = [];
  cells: HeatmapCell[] = [];
  months: MonthLabel[] = [];
  weekdays: string[] = [];
  total = 0;
  activeDays = 0;
  longestStreak = 0;
  busiest: HeatmapCell | null = null;
  /** The day whose card shows, and whether it stays open. */
  shown: HeatmapCell | null = null;
  shownOrigin: HTMLElement | null = null;
  pinned = false;
  /** The day that takes the focus when tabbing into the grid. */
  focusDate = '';

  readonly positions: ConnectedPosition[] = [
    { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -10 },
    { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: 10 },
    { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: 10 },
    { originX: 'start', originY: 'center', overlayX: 'end', overlayY: 'center', offsetX: -10 }
  ];

  ngOnChanges(): void {
    this.weeks = this.buildWeeks(this.activity ?? [], this.today ?? new Date());
    this.cells = this.weeks.flat();
    this.months = this.monthLabels(this.weeks);
    this.weekdays = this.weekdayNames();
    this.summarize();
    this.focusDate = this.cells.at(-1)?.date ?? '';
    this.close();
    // The latest weeks show first on a narrow screen.
    setTimeout(() => {
      const scroller = this.scroller?.nativeElement;
      if (scroller) {
        scroller.scrollLeft = scroller.scrollWidth;
      }
    });
  }

  /** Builds ~53 week-columns of daily cells ending at {@code today}. */
  buildWeeks(activity: ActivityDay[], today: Date): HeatmapCell[][] {
    const byDate = new Map(activity.map(day => [day.date, day]));
    const end = this.atMidnight(today);
    const start = this.startOfWeek(this.addDays(end, -370));

    const weeks: HeatmapCell[][] = [];
    let week: HeatmapCell[] = [];
    for (let day = start; day <= end; day = this.addDays(day, 1)) {
      const iso = toIsoDate(day);
      const found = byDate.get(iso);
      week.push({ date: iso, count: found?.count ?? 0, opportunities: found?.opportunities ?? 0, interviews: found?.interviews ?? 0,
        items: found?.items ?? [], week: weeks.length, weekday: (day.getDay() + 6) % 7 });
      if (day.getDay() === 0) {
        weeks.push(week);
        week = [];
      }
    }
    if (week.length > 0) {
      weeks.push(week);
    }
    return weeks;
  }

  /** Maps a count to a 0–4 shading level. */
  intensity(count: number): number {
    if (count <= 0) { return 0; }
    if (count <= 2) { return 1; }
    if (count <= 4) { return 2; }
    if (count <= 6) { return 3; }
    return 4;
  }

  /** The month names, each over the first week that holds its first day, two weeks apart at least. */
  monthLabels(weeks: HeatmapCell[][]): MonthLabel[] {
    const format = new Intl.DateTimeFormat(this.locale(), { month: 'short' });
    const labels: MonthLabel[] = [];
    weeks.forEach((week, index) => {
      const first = week.find(cell => cell.date.endsWith('-01'));
      if (first && (labels.length === 0 || index - labels.at(-1)!.week >= 2)) {
        labels.push({ week: index, name: format.format(this.parse(first.date)) });
      }
    });
    return labels;
  }

  /** Monday, Wednesday and Friday by their short names; the other rows unlabelled, as on GitHub. */
  weekdayNames(): string[] {
    const format = new Intl.DateTimeFormat(this.locale(), { weekday: 'short' });
    const monday = new Date(2024, 0, 1);
    return [0, 1, 2, 3, 4, 5, 6].map(offset => offset % 2 === 0 && offset < 6 ? format.format(this.addDays(monday, offset)) : '');
  }

  /** What a screen reader reads for the day. */
  label(cell: HeatmapCell): string {
    const day = new Intl.DateTimeFormat(this.locale(), { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })
      .format(this.parse(cell.date));
    return `${day}: ${this.activities(cell.count)}`;
  }

  /** "No activity", "1 activity", "3 activities". */
  activities(count: number): string {
    return count === 0 ? this.translate.instant('dashboard.heatmap.noActivity') : this.counted(count, 'activity', 'activities');
  }

  /** "2 opportunities · 1 interview", leaving out what did not happen. */
  split(cell: HeatmapCell): string {
    return [cell.opportunities ? this.counted(cell.opportunities, 'opportunity', 'opportunities') : '',
      cell.interviews ? this.counted(cell.interviews, 'interview', 'interviews') : ''].filter(Boolean).join(' · ');
  }

  /** A count with its noun, singular or plural. */
  counted(count: number, one: string, many: string): string {
    return this.translate.instant(`dashboard.heatmap.${count === 1 ? one : many}`, { count });
  }

  /** Shows the day's card on hover or focus, unless another one is kept open. */
  preview(cell: HeatmapCell, element: HTMLElement): void {
    if (!this.pinned) {
      this.shown = cell;
      this.shownOrigin = element;
    }
  }

  /** Hides a previewed card when the pointer or the focus leaves its day. */
  leave(cell: HeatmapCell): void {
    if (!this.pinned && this.shown === cell) {
      this.shown = null;
      this.shownOrigin = null;
    }
  }

  /** Keeps the day's card open, or closes it when it was. */
  toggle(cell: HeatmapCell, element: HTMLElement): void {
    if (this.pinned && this.shown === cell) {
      this.close();
      return;
    }
    this.shown = cell;
    this.shownOrigin = element;
    this.pinned = true;
    this.focusDate = cell.date;
  }

  close(): void {
    this.shown = null;
    this.shownOrigin = null;
    this.pinned = false;
  }

  /** Escape closes the card and gives the focus back to its day. */
  closeAndFocus(): void {
    const date = this.shown?.date;
    this.close();
    this.changes.markForCheck();
    if (date) {
      this.focus(date);
    }
  }

  /** The arrows move a day up or down, a week left or right; Home and End go to the first and last day. */
  onKeydown(event: KeyboardEvent, cell: HeatmapCell): void {
    const index = this.cells.indexOf(cell);
    const moves: Record<string, number> = { ArrowUp: -1, ArrowDown: 1, ArrowLeft: -7, ArrowRight: 7 };
    let target: number | null = null;
    if (event.key in moves) {
      target = index + moves[event.key];
    } else if (event.key === 'Home') {
      target = 0;
    } else if (event.key === 'End') {
      target = this.cells.length - 1;
    } else if (event.key === 'Escape' && this.shown) {
      event.preventDefault();
      this.closeAndFocus();
      return;
    }
    if (target === null) {
      return;
    }
    event.preventDefault();
    const next = this.cells[Math.min(this.cells.length - 1, Math.max(0, target))];
    this.close();
    this.focusDate = next.date;
    this.focus(next.date);
  }

  /** A day of the card's list, by its kind. */
  icon(item: ActivityItem): string {
    return item.kind === 'OPPORTUNITY' ? 'work_outline' : 'event';
  }

  private focus(date: string): void {
    this.host.nativeElement.querySelector<HTMLElement>(`[data-heatmap-day="${date}"]`)?.focus();
  }

  /** The total, the days with activity, the longest run of active days, and the busiest day. */
  private summarize(): void {
    this.total = this.cells.reduce((sum, cell) => sum + cell.count, 0);
    this.activeDays = this.cells.filter(cell => cell.count > 0).length;
    let run = 0;
    this.longestStreak = 0;
    this.busiest = null;
    for (const cell of this.cells) {
      run = cell.count > 0 ? run + 1 : 0;
      this.longestStreak = Math.max(this.longestStreak, run);
      if (cell.count > 0 && (!this.busiest || cell.count > this.busiest.count)) {
        this.busiest = cell;
      }
    }
  }

  private locale(): string {
    const language = this.translate.currentLang === 'en' || this.translate.currentLang === 'fr'
      ? this.translate.currentLang as AppLanguage : getSavedLanguage();
    return getAppLocale(language);
  }

  /** A local date from its ISO day, without the time zone shifting it. */
  parse(iso: string): Date {
    const [year, month, day] = iso.split('-').map(Number);
    return new Date(year, month - 1, day);
  }

  private atMidnight(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), date.getDate());
  }

  private addDays(date: Date, days: number): Date {
    const result = new Date(date);
    result.setDate(result.getDate() + days);
    return result;
  }

  /** Backs up to the Monday on or before the given date. */
  private startOfWeek(date: Date): Date {
    const result = this.atMidnight(date);
    const offset = (result.getDay() + 6) % 7; // days since Monday
    return this.addDays(result, -offset);
  }
}
