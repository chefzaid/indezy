import { ChangeDetectionStrategy, Component, Input, OnChanges, inject } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { Observable } from 'rxjs';

import { PurchaseOrder, PurchaseOrderAmendment, PurchaseOrderWrite } from '../../../models';
import { NotificationService } from '../../../services/notification/notification.service';
import { PurchaseOrderService } from '../../../services/purchase-order/purchase-order.service';
import { ConfirmDialogService } from '../../../shared/services/confirm-dialog.service';
import { PurchaseOrderDialogComponent, PurchaseOrderDialogData } from './purchase-order-dialog/purchase-order-dialog.component';

/** A mission's purchase orders and amendments, each with what its activity reports used of the ceiling. */
@Component({
  selector: 'app-purchase-orders-card',
  imports: [DatePipe, DecimalPipe, MatButtonModule, MatCardModule, MatIconModule, MatMenuModule, MatProgressBarModule,
    MatProgressSpinnerModule, TranslateModule],
  templateUrl: './purchase-orders-card.component.html',
  styleUrls: ['./purchase-orders-card.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PurchaseOrdersCardComponent implements OnChanges {
  @Input({ required: true }) projectId!: number;

  private readonly purchaseOrders = inject(PurchaseOrderService);
  private readonly dialog = inject(MatDialog);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly notifications = inject(NotificationService);

  orders: PurchaseOrder[] = [];
  loading = true;

  ngOnChanges(): void {
    this.load();
  }

  /** The share of the closest ceiling used, in percent, rounded down; none when no ceiling can be compared. */
  percent(order: PurchaseOrder): number | null {
    return order.usage.share === null ? null : Math.floor(order.usage.share * 100);
  }

  add(): void {
    this.write({}, write => this.purchaseOrders.create(this.projectId, write), 'purchaseOrders.created');
  }

  edit(order: PurchaseOrder): void {
    this.write({ order }, write => this.purchaseOrders.update(order.id, write), 'purchaseOrders.saved');
  }

  amend(order: PurchaseOrder): void {
    this.write({ amends: order }, write => this.purchaseOrders.create(this.projectId, write), 'purchaseOrders.amended');
  }

  editAmendment(order: PurchaseOrder, amendment: PurchaseOrderAmendment): void {
    this.write({ amendment, amends: order }, write => this.purchaseOrders.update(amendment.id, write), 'purchaseOrders.saved');
  }

  /** Deletes an amendment, or an order with its amendments, once confirmed. */
  remove(id: number, number: string, withAmendments: boolean): void {
    this.confirm.confirm({
      titleKey: 'purchaseOrders.deleteTitle', messageKey: withAmendments ? 'purchaseOrders.deleteWithAmendments' : 'purchaseOrders.deleteMessage',
      messageParams: { number }, confirmKey: 'common.delete', danger: true
    }).subscribe(confirmed => {
      if (!confirmed) {
        return;
      }
      this.purchaseOrders.delete(id).subscribe({
        next: () => {
          this.notifications.success('purchaseOrders.deleted');
          this.load();
        },
        error: () => this.notifications.error('purchaseOrders.saveError')
      });
    });
  }

  private load(): void {
    this.loading = true;
    this.purchaseOrders.list(this.projectId).subscribe({
      next: orders => {
        this.orders = orders;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
        this.notifications.error('purchaseOrders.loadError');
      }
    });
  }

  /** Opens the dialog, then shows the order as the server answered, last in the list when new. */
  private write(data: PurchaseOrderDialogData, request: (write: PurchaseOrderWrite) => Observable<PurchaseOrder>, successKey: string): void {
    this.dialog.open<PurchaseOrderDialogComponent, PurchaseOrderDialogData, PurchaseOrderWrite>(PurchaseOrderDialogComponent, {
      data, width: '560px', maxWidth: '95vw', autoFocus: 'first-tabbable'
    }).afterClosed().subscribe(write => {
      if (!write) {
        return;
      }
      request(write).subscribe({
        next: saved => {
          const known = this.orders.some(order => order.id === saved.id);
          this.orders = known ? this.orders.map(order => order.id === saved.id ? saved : order) : [...this.orders, saved];
          this.notifications.success(successKey);
        },
        error: () => this.notifications.error('purchaseOrders.saveError')
      });
    });
  }
}
