import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';

import { EsnFigures } from '../../../services/esn-scorecard/esn-scorecard.service';

/** An ESN's figures across members as a list of terms, each shown once enough freelancers give it. */
@Component({
  selector: 'app-esn-figures',
  imports: [TranslateModule],
  template: `
    <dl class="figures">
      @if (figures.offerRate !== null) {
        <div data-esn-offer-rate><dt>{{ 'esnScorecard.offerRate' | translate }}</dt><dd>{{ figures.offerRate }} %</dd></div>
      }
      @if (figures.processDays !== null) {
        <div data-esn-process><dt>{{ 'esnScorecard.process' | translate }}</dt><dd>{{ 'esnScorecard.days' | translate:{ days: figures.processDays } }}</dd></div>
      }
      @if (figures.marginLow !== null) {
        <div data-esn-margin><dt>{{ 'esnScorecard.margin' | translate }}</dt><dd>{{ 'esnScorecard.marginValue' | translate:{ low: figures.marginLow, high: figures.marginHigh } }}</dd></div>
      }
      @if (figures.answerDays !== null) {
        <div data-esn-answer><dt>{{ 'esnScorecard.answer' | translate }}</dt><dd>{{ 'esnScorecard.days' | translate:{ days: figures.answerDays } }}</dd></div>
      }
      @if (figures.paymentDays !== null) {
        <div data-esn-payment><dt>{{ 'esnScorecard.payment' | translate }}</dt><dd>{{ 'esnScorecard.days' | translate:{ days: figures.paymentDays } }}</dd></div>
      }
    </dl>
  `,
  styles: [`
    .figures {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
      gap: 4px 12px;
      margin: 4px 0 0;
    }

    dt {
      color: var(--app-text-muted);
      font-size: 0.8125rem;
    }

    dd {
      margin: 0;
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class EsnFiguresComponent {
  @Input({ required: true }) figures!: EsnFigures;
}
