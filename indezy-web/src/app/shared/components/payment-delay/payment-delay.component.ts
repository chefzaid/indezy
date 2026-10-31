import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnChanges, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { PaymentDelay } from '../../../models';
import { InvoiceService } from '../../../services/invoice/invoice.service';

/** How a client really pays, from its paid invoices: shown on its page and on the opportunities it pays for. */
@Component({
  selector: 'app-payment-delay',
  imports: [MatIconModule, TranslateModule],
  template: `
    @if (delay) {
      <span class="payment-delay" [class.late]="delay.averageDaysLate > 0" data-payment-delay>
        <mat-icon aria-hidden="true">schedule</mat-icon>
        {{ (delay.averageDaysLate > 0 ? 'invoices.delay.late' : 'invoices.delay.onTime') | translate:delay }}
      </span>
    }
  `,
  styles: [`
    .payment-delay {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      color: var(--app-text-muted);
      font-size: 0.875rem;

      mat-icon {
        width: 18px;
        height: 18px;
        font-size: 18px;
      }

      &.late {
        color: var(--app-danger);
      }
    }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PaymentDelayComponent implements OnChanges {
  /** The client invoices go to. */
  @Input() clientId?: number | null;
  delay?: PaymentDelay;

  private readonly invoices = inject(InvoiceService);
  private readonly changes = inject(ChangeDetectorRef);

  ngOnChanges(): void {
    this.delay = undefined;
    if (!this.clientId) {
      return;
    }
    const clientId = this.clientId;
    // Without invoices (or their feature), the line stays empty.
    this.invoices.paymentDelays().subscribe({
      next: delays => {
        this.delay = delays.find(delay => delay.clientId === clientId);
        this.changes.markForCheck();
      },
      error: () => this.delay = undefined
    });
  }
}
