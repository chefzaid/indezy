import { Component, Input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';
import { BilledDays } from '../../../models/project.models';

/** This year's days of validated activity reports against the profile's yearly target, and the year at this pace. */
@Component({
  selector: 'app-billed-days',
  imports: [DecimalPipe, TranslateModule, MatIconModule],
  template: `
    <section [attr.aria-label]="'dashboard.billedDays.title' | translate:{ year: days.year }" data-billed-days>
      <h3><mat-icon>event_available</mat-icon>{{ 'dashboard.billedDays.title' | translate:{ year: days.year } }}</h3>
      <dl>
        <div>
          <dt>{{ 'dashboard.billedDays.billed' | translate }}</dt>
          <dd data-billed-days-billed>{{ days.billed | number:'1.0-1' }}
            @if (days.target) { / {{ days.target }} ({{ share() }} %) }
          </dd>
        </div>
        <div>
          <dt>{{ 'dashboard.billedDays.projected' | translate }}</dt>
          <dd data-billed-days-projected>{{ days.projected ?? '—' }}</dd>
        </div>
      </dl>
      @if (gap(); as gap) {
        <p [class.short]="gap < 0" data-billed-days-gap>
          {{ (gap < 0 ? 'dashboard.billedDays.short' : 'dashboard.billedDays.ahead') | translate:{ days: abs(gap) } }}
        </p>
      }
    </section>
  `,
  styles: `
    h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; font-size: 1rem; }
    dl { margin: 0; }
    dl div { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; padding: 8px 0; }
    dt { color: var(--app-text-muted); }
    dd { margin: 0; white-space: nowrap; font-weight: 600; }
    p { margin: 8px 0 0; color: var(--app-positive); }
    p.short { color: var(--app-danger); }
  `
})
export class BilledDaysComponent {
  @Input({ required: true }) days!: BilledDays;

  /** The target's share already billed, in whole percents. */
  share(): number {
    return Math.round(this.days.billed / this.days.target! * 100);
  }

  /** Days the projection exceeds (positive) or misses (negative) the target, null without both or when they match. */
  gap(): number | null {
    return this.days.target && this.days.projected !== null ? this.days.projected - this.days.target || null : null;
  }

  abs(value: number): number {
    return Math.abs(value);
  }
}
