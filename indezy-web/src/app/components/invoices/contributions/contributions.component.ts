import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';

import { ContributionEstimate, ContributionPeriod } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';

/**
 * The year's URSSAF contributions on the revenue received (see docs/features.md#urssaf-contributions): for a
 * micro-entreprise, each month or quarter to declare with its deadline, the next one first; for another legal form, the
 * estimate at the member's own rate. Shown for a micro-entreprise, or when an own rate is set.
 */
@Component({
  selector: 'app-contributions',
  imports: [DatePipe, DecimalPipe, RouterLink, MatCardModule, TranslateModule],
  templateUrl: './contributions.component.html',
  styleUrls: ['./contributions.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ContributionsComponent implements OnInit {
  private readonly invoices = inject(InvoiceService);
  private readonly changes = inject(ChangeDetectorRef);

  estimate: ContributionEstimate | null = null;

  ngOnInit(): void {
    this.invoices.contributions().subscribe({
      next: estimate => {
        // A micro-entreprise always, another form with a rate, or an EI past the official table (asked for its own rate).
        this.estimate = estimate.legalForm === 'MICRO_ENTREPRISE' || estimate.rate !== null || estimate.profit != null ? estimate : null;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** The periods, latest first: the one to declare now leads. */
  get periods(): ContributionPeriod[] {
    return [...(this.estimate?.periods ?? [])].reverse();
  }

  /** "Q3" for a quarter, else null (a month is written by the date pipe). */
  quarter(period: ContributionPeriod): string | null {
    return this.estimate?.frequency === 'QUARTERLY' ? 'Q' + (Math.floor(Number(period.from.substring(5, 7)) / 3) + 1) : null;
  }
}
