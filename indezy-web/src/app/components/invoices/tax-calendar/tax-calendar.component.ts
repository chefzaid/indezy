import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { TranslateModule } from '@ngx-translate/core';

import { TaxDeadline } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';

/**
 * The tax deadlines of the next twelve months beside the URSSAF declarations (see docs/features.md#tax-calendar): the
 * CFE, and while charging VAT the simplified regime's instalments and annual return, the VAT estimated from the payments
 * received.
 */
@Component({
  selector: 'app-tax-calendar',
  imports: [DatePipe, DecimalPipe, MatCardModule, TranslateModule],
  template: `
    @if (deadlines.length) {
      <mat-card class="tax-calendar" data-tax-calendar>
        <mat-card-header>
          <mat-card-title>{{ 'invoices.taxCalendar.title' | translate }}</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <ul>
            @for (deadline of deadlines; track deadline.kind + deadline.dueOn) {
              <li [attr.data-tax-deadline]="deadline.kind">
                <span class="when">{{ deadline.monthOnly ? (deadline.dueOn | date:'MMMM y') : (deadline.dueOn | date:'d MMMM y') }}</span>
                <span class="what">{{ 'invoices.taxCalendar.kinds.' + deadline.kind | translate }}</span>
                @if (deadline.amount !== null) {
                  <span class="amount">≈ {{ deadline.amount | number:'1.0-0' }} €</span>
                }
              </li>
            }
          </ul>
          <p class="note">{{ 'invoices.taxCalendar.basis' | translate }}</p>
        </mat-card-content>
      </mat-card>
    }
  `,
  styles: [`
    .tax-calendar {
      margin-bottom: 16px;
    }

    ul {
      margin: 0;
      padding: 0;
      list-style: none;
    }

    li {
      display: grid;
      grid-template-columns: minmax(110px, 1fr) minmax(0, 2fr) auto;
      gap: 12px;
      align-items: center;
      padding: 4px 0;
      border-top: 1px solid var(--app-border);
    }

    .when::first-letter {
      text-transform: uppercase;
    }

    .amount {
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }

    .note {
      margin: 12px 0 0;
      color: var(--app-text-muted);
      font-size: 0.875rem;
    }

    @media (max-width: 599px) {
      li {
        grid-template-columns: minmax(0, 1fr) auto;

        .what {
          grid-column: 1 / -1;
          grid-row: 2;
        }
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class TaxCalendarComponent implements OnInit {
  private readonly invoices = inject(InvoiceService);
  private readonly changes = inject(ChangeDetectorRef);

  deadlines: TaxDeadline[] = [];

  ngOnInit(): void {
    this.invoices.taxCalendar().subscribe({
      next: deadlines => {
        this.deadlines = Array.isArray(deadlines) ? deadlines : [];
        this.changes.markForCheck();
      },
      error: () => undefined
    });
  }
}
