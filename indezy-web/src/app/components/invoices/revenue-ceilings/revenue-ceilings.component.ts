import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { Ceiling, RevenueCeilings } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';

/** When a franchise ends, as the message says it: already this year, on the day it was passed, or next 1 January. */
export type EndKind = 'past' | 'day' | 'next';

/**
 * The year's revenue received against the micro-entreprise ceiling and the VAT franchise thresholds for services
 * (see docs/features.md#revenue-ceilings), with a warning when near or past one and the day the regime or the franchise
 * ends; shown only when one applies.
 */
@Component({
  selector: 'app-revenue-ceilings',
  imports: [DatePipe, DecimalPipe, MatCardModule, MatIconModule, TranslateModule],
  templateUrl: './revenue-ceilings.component.html',
  styleUrls: ['./revenue-ceilings.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class RevenueCeilingsComponent implements OnInit {
  private readonly invoices = inject(InvoiceService);
  private readonly changes = inject(ChangeDetectorRef);

  ceilings: RevenueCeilings | null = null;

  ngOnInit(): void {
    this.invoices.ceilings().subscribe({
      next: ceilings => {
        this.ceilings = ceilings.micro || ceilings.vat ? ceilings : null;
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }

  /** The share drawn on the bar, at most the whole bar. */
  width(ceiling: Ceiling): number {
    return Math.min(100, ceiling.percent);
  }

  /** Whether the franchise ended on 1 January of the year, ends on the day it was passed, or on the next 1 January. */
  endKind(ceiling: Ceiling, year: number): EndKind {
    if (ceiling.endsOn === `${year}-01-01`) {
      return 'past';
    }
    return ceiling.endsOn && ceiling.endsOn.startsWith(String(year)) ? 'day' : 'next';
  }
}
