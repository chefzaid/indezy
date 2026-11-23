import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { EsnFigures, EsnScorecardService } from '../../../services/esn-scorecard/esn-scorecard.service';
import { EsnFiguresComponent } from './esn-figures.component';

/**
 * An ESN's scorecard across members, on its page: its figures once five freelancers back them, else how many do;
 * nothing for a reader outside the network or an ESN no member dealt with.
 */
@Component({
  selector: 'app-members-scorecard',
  imports: [RouterLink, TranslateModule, EsnFiguresComponent],
  template: `
    @if (figures && figures.freelancers > 0) {
      <section class="members-scorecard" data-members-scorecard [attr.aria-label]="'esnScorecard.membersTitle' | translate">
        <h3>{{ 'esnScorecard.membersTitle' | translate }}</h3>
        @if (figures.freelancers >= figures.threshold) {
          <p data-members-scorecard-count>{{ 'esnScorecard.backedBy' | translate:{ count: figures.freelancers } }}</p>
          <app-esn-figures [figures]="figures"></app-esn-figures>
        } @else {
          <p data-members-scorecard-too-few>{{ 'esnScorecard.tooFew' | translate:{ count: figures.freelancers, threshold: figures.threshold } }}</p>
        }
        <p class="hint">{{ (figures.sharing ? 'esnScorecard.yoursCount' : 'esnScorecard.yoursLeftOut') | translate }}
          <a routerLink="/profile" [queryParams]="{ tab: 'privacy' }">{{ 'esnScorecard.privacy' | translate }}</a></p>
      </section>
    }
  `,
  styles: [`
    .members-scorecard {
      margin-top: 12px;

      h3 {
        margin: 0 0 4px;
        font-size: 1rem;
      }

      p {
        margin: 0 0 4px;
      }

      .hint {
        margin-top: 8px;
        color: var(--app-text-muted);
        font-size: 0.8125rem;
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MembersScorecardComponent implements OnChanges {
  /** The ESN, in the workspace. */
  @Input() clientId?: number | null;
  figures?: EsnFigures;

  private readonly scorecards = inject(EsnScorecardService);
  private readonly changes = inject(ChangeDetectorRef);

  ngOnChanges(): void {
    this.figures = undefined;
    if (!this.clientId) {
      return;
    }
    this.scorecards.company(this.clientId).subscribe({
      next: figures => {
        this.figures = figures;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }
}
