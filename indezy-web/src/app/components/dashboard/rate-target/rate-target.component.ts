import { Component, Input } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { MatIconModule } from '@angular/material/icon';

/** Compares the profile objective with recorded rates of accepted missions in the current view. */
@Component({
  selector: 'app-rate-target',
  imports: [CurrencyPipe, TranslateModule, MatIconModule],
  template: `
    <section [attr.aria-label]="'dashboard.rateTargetComparison' | translate">
      <h3><mat-icon>flag</mat-icon>{{ 'dashboard.rateTargetComparison' | translate }}</h3>
      <dl>
        <div>
          <dt>{{ 'profile.targetDailyRate' | translate }}</dt>
          <dd>{{ target | currency:'EUR':'symbol':'1.0-0' }}</dd>
        </div>
        <div>
          <dt>{{ 'dashboard.averageObtainedDailyRate' | translate }}</dt>
          <dd>{{ obtained == null ? '—' : (obtained | currency:'EUR':'symbol':'1.0-0') }}</dd>
        </div>
      </dl>
    </section>
  `,
  styles: `
    h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 16px; font-size: 1rem; }
    dl { margin: 0; }
    dl div { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; padding: 8px 0; }
    dt { color: var(--app-text-muted); }
    dd { margin: 0; white-space: nowrap; font-weight: 600; }
  `
})
export class RateTargetComponent {
  @Input({ required: true }) target!: number;
  @Input() obtained: number | null | undefined = null;
}
