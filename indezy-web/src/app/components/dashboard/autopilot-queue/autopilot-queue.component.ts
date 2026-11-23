import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { AiAutopilotQueue, AiPreparedOffer } from '../../../models';
import { AiService } from '../../../services/ai/ai.service';

/** How many prepared offers the dashboard lists. */
export const PREPARED_SHOWN = 5;

/**
 * **Prepared overnight** (docs/features.md#daily-autopilot): when the daily autopilot run is on, what it triaged in the
 * last day, those worth applying to first, each opening its offer; nothing while it is off or found nothing.
 */
@Component({
  selector: 'app-autopilot-queue',
  imports: [RouterLink, MatIconModule, TranslateModule],
  template: `
    @if (queue && queue.on && queue.offers.length) {
      <section class="autopilot" data-autopilot-queue aria-labelledby="autopilot-title">
        <h3 id="autopilot-title"><mat-icon aria-hidden="true">auto_awesome</mat-icon>{{ 'dashboard.autopilot.title' | translate }}</h3>
        <p data-autopilot-summary>{{ 'dashboard.autopilot.summary' | translate:{ count: queue.offers.length, fit: fit } }}</p>
        <ul>
          @for (offer of shown; track offer.offerId) {
            <li [attr.data-autopilot-offer]="offer.offerId" [class.fit]="offer.goodFit">
              <a [routerLink]="['/offers', offer.offerId]">{{ offer.title }}@if (offer.company) { · {{ offer.company }} }</a>
              <span class="verdict">{{ offer.verdict }}</span>
              @if (offer.dealBreakers.length) {
                <span class="breakers">{{ offer.dealBreakers.join(' · ') }}</span>
              }
            </li>
          }
        </ul>
      </section>
    }
  `,
  styles: [`
    .autopilot {
      margin: 0 0 16px;
      padding: 12px 16px;
      border: 1px solid var(--app-border);
      border-radius: var(--app-radius, 12px);
      background: var(--app-surface);

      h3 {
        display: flex;
        gap: 8px;
        align-items: center;
        margin: 0 0 4px;
        font-size: 1rem;
      }

      p {
        margin: 0 0 8px;
        color: var(--app-text-muted);
      }

      ul {
        margin: 0;
        padding: 0;
        list-style: none;
      }

      li {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: 6px 0;
        border-top: 1px solid var(--app-border);
      }

      li.fit a {
        font-weight: 600;
      }

      a {
        color: var(--app-link);
      }

      .verdict,
      .breakers {
        font-size: 0.875rem;
      }

      .breakers {
        color: var(--app-danger);
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class AutopilotQueueComponent implements OnInit {
  private readonly ai = inject(AiService);
  private readonly changes = inject(ChangeDetectorRef);

  queue: AiAutopilotQueue | null = null;

  ngOnInit(): void {
    this.ai.autopilot().subscribe({
      next: queue => {
        this.queue = Array.isArray(queue?.offers) ? queue : null;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** How many are worth applying to. */
  get fit(): number {
    return this.queue?.offers.filter(offer => offer.goodFit).length ?? 0;
  }

  get shown(): AiPreparedOffer[] {
    return this.queue?.offers.slice(0, PREPARED_SHOWN) ?? [];
  }
}
