import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

import { EsnBoard, EsnScorecardService } from '../../../services/esn-scorecard/esn-scorecard.service';
import { EsnFiguresComponent } from '../../../shared/components/members-scorecard/esn-figures.component';

/** How many ESNs the card lists before **Show all**. */
export const ESNS_SHOWN = 3;

/**
 * The ESN scorecards beside the feed (docs/adr/0016): the ESNs five freelancers or more back, the most backed first, each
 * with its figures across members.
 */
@Component({
  selector: 'app-esn-scorecards-card',
  imports: [RouterLink, TranslateModule, EsnFiguresComponent],
  template: `
    @if (board; as board) {
      <section class="side-card esn-scorecards" data-esn-scorecards>
        <h2>{{ 'esnScorecard.title' | translate }}</h2>
        @if (board.companies.length === 0) {
          <p data-esn-scorecards-empty>{{ 'esnScorecard.empty' | translate:{ threshold: board.threshold } }}</p>
        } @else {
          <ul>
            @for (company of (all ? board.companies : board.companies.slice(0, shown)); track company.company) {
              <li [attr.data-esn-scorecard]="company.company">
                <span class="name">{{ company.company }}</span>
                <span class="count">{{ 'esnScorecard.backedBy' | translate:{ count: company.freelancers } }}</span>
                <app-esn-figures [figures]="company"></app-esn-figures>
              </li>
            }
          </ul>
          @if (!all && board.companies.length > shown) {
            <button type="button" class="more" (click)="all = true" data-esn-scorecards-all>{{ 'esnScorecard.showAll' | translate:{ count: board.companies.length } }}</button>
          }
        }
        @if (!board.sharing) {
          <a class="share" routerLink="/profile" [queryParams]="{ tab: 'privacy' }" data-esn-scorecards-share>{{ 'esnScorecard.share' | translate }}</a>
        }
      </section>
    }
  `,
  styleUrls: ['../rate-benchmark/rate-benchmark-card.component.scss'],
  styles: [`
    ul {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li + li {
      margin-top: 12px;
      padding-top: 12px;
      border-top: 1px solid var(--app-border);
    }

    .name {
      display: block;
      font-weight: 600;
    }

    .count {
      color: var(--app-text-muted);
      font-size: 0.8125rem;
    }

    .more {
      margin-top: 12px;
      padding: 0;
      border: none;
      background: none;
      color: var(--app-link);
      font: inherit;
      font-size: 0.875rem;
      cursor: pointer;
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class EsnScorecardsCardComponent implements OnInit {
  private readonly scorecards = inject(EsnScorecardService);
  private readonly changes = inject(ChangeDetectorRef);

  readonly shown = ESNS_SHOWN;
  board: EsnBoard | null = null;
  all = false;

  ngOnInit(): void {
    this.scorecards.board().subscribe({
      next: board => {
        this.board = Array.isArray(board?.companies) ? board : null;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }
}
