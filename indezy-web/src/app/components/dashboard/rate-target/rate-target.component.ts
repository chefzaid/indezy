import { Component, Input, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NetworkShare, ShareToNetworkComponent } from '../../network/share/share-to-network.component';
import { MatIconModule } from '@angular/material/icon';

/** Compares the profile objective with recorded rates of accepted missions in the current view. */
@Component({
  selector: 'app-rate-target',
  imports: [CurrencyPipe, TranslateModule, MatIconModule, ShareToNetworkComponent],
  template: `
    <section [attr.aria-label]="'dashboard.rateTargetComparison' | translate">
      <div class="head">
        <h3><mat-icon>flag</mat-icon>{{ 'dashboard.rateTargetComparison' | translate }}</h3>
        @if (obtained != null) {
          <app-share-to-network [build]="share" data-share-rate></app-share-to-network>
        }
      </div>
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
    .head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; margin: 0 0 16px; }
    h3 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 1rem; }
    dl { margin: 0; }
    dl div { display: flex; justify-content: space-between; align-items: baseline; gap: 16px; padding: 8px 0; }
    dt { color: var(--app-text-muted); }
    dd { margin: 0; white-space: nowrap; font-weight: 600; }
  `
})
export class RateTargetComponent {
  @Input({ required: true }) target!: number;
  @Input() obtained: number | null | undefined = null;
  private readonly translate = inject(TranslateService);

  /** The average rate obtained against the objective, as a post to read again before publishing. */
  readonly share = (): NetworkShare => ({ text: this.translate.instant('dashboard.rateShare', { obtained: Math.round(this.obtained ?? 0),
    target: Math.round(this.target) }), kind: 'POST', details: null });
}
