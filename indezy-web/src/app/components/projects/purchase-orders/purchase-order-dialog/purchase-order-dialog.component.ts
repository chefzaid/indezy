import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

import { PurchaseOrder, PurchaseOrderAmendment, PurchaseOrderWrite } from '../../../../models';

export interface PurchaseOrderDialogData {
  /** The order to change. */
  order?: PurchaseOrder;
  /** The amendment to change. */
  amendment?: PurchaseOrderAmendment;
  /** The order an amendment, new or changed, adds to, and when it starts. */
  amends?: { id: number; periodStart: string };
}

/**
 * Writes a purchase order (number, period, a ceiling in days or euros, notes) or an amendment to one (days or euros
 * added, or a later end).
 */
@Component({
  selector: 'app-purchase-order-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, TranslateModule],
  templateUrl: './purchase-order-dialog.component.html',
  styleUrls: ['./purchase-order-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class PurchaseOrderDialogComponent {
  readonly data = inject<PurchaseOrderDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<PurchaseOrderDialogComponent, PurchaseOrderWrite>);
  private readonly fb = inject(FormBuilder);

  readonly isAmendment = !!this.data.amends;
  private readonly existing = this.data.order ?? this.data.amendment;

  readonly form = this.fb.group({
    number: [this.existing?.number ?? '', [Validators.required, Validators.maxLength(60)]],
    periodStart: [this.data.order?.periodStart ?? '', this.isAmendment ? [] : [Validators.required]],
    periodEnd: [this.existing?.periodEnd ?? ''],
    dayCeiling: [this.existing?.dayCeiling ?? null as number | null, [Validators.min(0.5), Validators.max(9999)]],
    amountCeiling: [this.existing?.amountCeiling ?? null as number | null, [Validators.min(1), Validators.max(9999999)]],
    notes: [this.existing?.notes ?? '', Validators.maxLength(500)]
  }, { validators: control => this.check(control) });

  get titleKey(): string {
    if (this.isAmendment) {
      return this.data.amendment ? 'purchaseOrders.editAmendmentTitle' : 'purchaseOrders.amendTitle';
    }
    return this.data.order ? 'purchaseOrders.editTitle' : 'purchaseOrders.newTitle';
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({
      amendsId: this.data.amends?.id ?? null,
      number: value.number!.trim(),
      periodStart: this.isAmendment ? null : value.periodStart || null,
      periodEnd: value.periodEnd || null,
      dayCeiling: value.dayCeiling ?? null,
      amountCeiling: value.amountCeiling ?? null,
      notes: value.notes?.trim() || null
    });
  }

  /** An order needs a ceiling, an amendment something to add, and neither ends before the order starts. */
  private check(control: AbstractControl): ValidationErrors | null {
    const { periodStart, periodEnd, dayCeiling, amountCeiling } = control.value;
    const hasCeiling = dayCeiling !== null && dayCeiling !== '' || amountCeiling !== null && amountCeiling !== '';
    const start = this.isAmendment ? this.data.amends!.periodStart : periodStart;
    if (periodEnd && start && periodEnd < start) {
      return { endBeforeStart: true };
    }
    if (this.isAmendment) {
      return hasCeiling || periodEnd ? null : { nothingAdded: true };
    }
    return hasCeiling ? null : { ceilingRequired: true };
  }
}
