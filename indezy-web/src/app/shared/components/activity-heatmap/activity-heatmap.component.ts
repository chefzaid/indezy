import { Component, Input, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { ActivityDay } from '../../../models';

export interface HeatmapCell {
  date: string;
  count: number;
}

/**
 * GitHub-style activity heatmap: a column per week (Monday–Sunday) over the last year,
 * each day shaded by its prospection activity count.
 */
@Component({
  selector: 'app-activity-heatmap',
  imports: [CommonModule, TranslateModule],
  templateUrl: './activity-heatmap.component.html',
  styleUrls: ['./activity-heatmap.component.scss']
})
export class ActivityHeatmapComponent implements OnChanges {
  @Input() activity: ActivityDay[] = [];

  weeks: HeatmapCell[][] = [];

  ngOnChanges(): void {
    this.weeks = this.buildWeeks(this.activity ?? [], new Date());
  }

  /** Builds ~53 week-columns of daily cells ending at {@code today}. */
  buildWeeks(activity: ActivityDay[], today: Date): HeatmapCell[][] {
    const counts = new Map(activity.map(day => [day.date, day.count]));
    const end = this.atMidnight(today);
    const start = this.startOfWeek(this.addDays(end, -370));

    const weeks: HeatmapCell[][] = [];
    let week: HeatmapCell[] = [];
    for (let day = start; day <= end; day = this.addDays(day, 1)) {
      const iso = this.toIso(day);
      week.push({ date: iso, count: counts.get(iso) ?? 0 });
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

  private toIso(date: Date): string {
    const month = `${date.getMonth() + 1}`.padStart(2, '0');
    const day = `${date.getDate()}`.padStart(2, '0');
    return `${date.getFullYear()}-${month}-${day}`;
  }
}
