import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { CompanyScorecard } from '../../../models/client.models';
import { ClientService } from '../../../services/client/client.service';

/**
 * How a company dealt with the workspace, on its page: the opportunities with it, the offer rate, the process length,
 * an ESN's margin and its answer time, each figure shown once something backs it; nothing without an opportunity.
 */
@Component({
  selector: 'app-company-scorecard',
  imports: [TranslateModule],
  template: `
    @if (scorecard && scorecard.opportunities) {
      <section class="scorecard" data-company-scorecard [attr.aria-label]="'clients.detail.scorecard.title' | translate">
        <h3>{{ 'clients.detail.scorecard.title' | translate }}</h3>
        <p data-scorecard-opportunities>{{ 'clients.detail.scorecard.opportunities' | translate:scorecard }}</p>
        <dl>
          @if (scorecard.offerRate !== null) {
            <div data-scorecard-offer-rate>
              <dt>{{ 'clients.detail.scorecard.offerRate' | translate }}</dt>
              <dd>{{ 'clients.detail.scorecard.offerRateValue' | translate:{ rate: scorecard.offerRate } }}</dd>
            </div>
          }
          @if (scorecard.processDays !== null) {
            <div data-scorecard-process>
              <dt>{{ 'clients.detail.scorecard.process' | translate }}</dt>
              <dd>{{ 'clients.detail.scorecard.processValue' | translate:{ days: scorecard.processDays, count: scorecard.processes } }}</dd>
            </div>
          }
          @if (scorecard.marginMin !== null) {
            <div data-scorecard-margin>
              <dt>{{ 'clients.detail.scorecard.margin' | translate }}</dt>
              <dd>{{ (scorecard.margins > 1 ? 'clients.detail.scorecard.marginValue' : 'clients.detail.scorecard.marginOne') | translate:{
                min: scorecard.marginMin, max: scorecard.marginMax, count: scorecard.margins } }}</dd>
            </div>
          }
          @if (scorecard.answerDays !== null) {
            <div data-scorecard-answer>
              <dt>{{ 'clients.detail.scorecard.answer' | translate }}</dt>
              <dd>{{ 'clients.detail.scorecard.answerValue' | translate:{ days: scorecard.answerDays } }}</dd>
            </div>
          }
        </dl>
        <p class="hint">{{ 'clients.detail.scorecard.private' | translate }}</p>
      </section>
    }
  `,
  styles: [`
    .scorecard {
      margin-top: 12px;

      h3 {
        margin: 0 0 4px;
        font-size: 1rem;
      }

      p {
        margin: 0 0 4px;
      }

      dl {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
        gap: 8px 16px;
        margin: 8px 0;
      }

      dt {
        color: var(--app-text-muted);
        font-size: 0.8125rem;
      }

      dd {
        margin: 0;
      }

      .hint {
        color: var(--app-text-muted);
        font-size: 0.8125rem;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class CompanyScorecardComponent implements OnChanges {
  /** The client or ESN. */
  @Input() clientId?: number | null;
  scorecard?: CompanyScorecard;

  private readonly clients = inject(ClientService);
  private readonly changes = inject(ChangeDetectorRef);

  ngOnChanges(): void {
    this.scorecard = undefined;
    if (!this.clientId) {
      return;
    }
    this.clients.getScorecard(this.clientId).subscribe({
      next: scorecard => {
        this.scorecard = scorecard;
        this.changes.markForCheck();
      },
      error: () => this.scorecard = undefined
    });
  }
}
