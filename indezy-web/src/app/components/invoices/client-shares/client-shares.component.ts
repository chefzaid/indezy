import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { Invoice } from '../../../models';
import { ClientShare, DEPENDENCE_PERCENT, clientShares } from './client-shares';

/**
 * The revenue of the last 12 months by client, with a warning when one client pays most of it: a freelancer working
 * mostly for one client looks less independent, which URSSAF and the courts weigh when they reclassify a contract.
 */
@Component({
  selector: 'app-client-shares',
  imports: [DecimalPipe, RouterLink, MatCardModule, MatIconModule, TranslateModule],
  templateUrl: './client-shares.component.html',
  styleUrls: ['./client-shares.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ClientSharesComponent {
  shares: ClientShare[] = [];

  @Input() set invoices(invoices: Invoice[]) {
    this.shares = clientShares(invoices);
  }

  /** The client paying half the revenue or more, if one does. */
  get dependence(): ClientShare | undefined {
    const top = this.shares[0];
    return top && top.percent >= DEPENDENCE_PERCENT ? top : undefined;
  }
}
