import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { AuthService } from '../../../services/auth/auth.service';
import { CommuteService } from '../../../services/commute/commute.service';
import { CommuteStats, LONG_COMMUTE_HOURS, commuteStats } from './commute-stats';

/** The pipeline's commute: its monthly hours, the won missions', the remote share and what long commutes pay. */
@Component({
  selector: 'app-commute-stats',
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [DecimalPipe, MatIconModule, TranslateModule],
  // The parent styles this element as a card: hidden until there is something to show.
  host: { '[style.display]': "visible ? null : 'none'" },
  template: `
    @if (stats && visible) {
      <section [attr.aria-label]="'dashboard.commute.title' | translate" data-commute-stats>
        <h3><mat-icon>commute</mat-icon>{{ 'dashboard.commute.title' | translate }}</h3>
        <dl>
          <div>
            <dt>{{ 'dashboard.commute.pipeline' | translate }}</dt>
            <dd data-commute-pipeline>{{ stats.pipelineHours === null ? '—' : ('dashboard.commute.hours' | translate:{ hours: (stats.pipelineHours | number:'1.0-1') }) }}</dd>
          </div>
          <div>
            <dt>{{ 'dashboard.commute.won' | translate }}</dt>
            <dd data-commute-won>{{ stats.wonHours === null ? '—' : ('dashboard.commute.hours' | translate:{ hours: (stats.wonHours | number:'1.0-1') }) }}</dd>
          </div>
          <div>
            <dt>{{ 'dashboard.commute.remote' | translate }}</dt>
            <dd data-commute-remote>{{ stats.remotePercent }} %</dd>
          </div>
        </dl>
        @if (stats.longRate !== null) {
          <p data-commute-rates>{{ 'dashboard.commute.rates' | translate:{ hours: longHours, long: stats.longRate, short: stats.shortRate } }}</p>
        }
      </section>
    }
  `,
  styles: `
    h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; font-size: 1rem; }
    dl { margin: 0; }
    dl div { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; padding: 8px 0; }
    dt { color: var(--app-text-muted); }
    dd { margin: 0; white-space: nowrap; font-weight: 600; }
    p { margin: 8px 0 0; color: var(--app-text-muted); font-size: 0.875rem; }
  `
})
export class CommuteStatsComponent implements OnInit {
  private readonly commutes = inject(CommuteService);
  private readonly auth = inject(AuthService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly longHours = LONG_COMMUTE_HOURS;
  stats: CommuteStats | null = null;

  get visible(): boolean {
    return this.stats !== null && (this.stats.pipelineHours !== null || this.stats.wonHours !== null);
  }

  ngOnInit(): void {
    const freelanceId = this.auth.getUser()?.id;
    if (!freelanceId) {
      return;
    }
    this.commutes.getProjectsSortedByCommute(freelanceId, 'DRIVING').subscribe({
      next: results => {
        this.stats = commuteStats(results);
        this.changes.markForCheck();
      },
      error: () => {
        this.stats = null;
        this.changes.markForCheck();
      }
    });
  }
}
