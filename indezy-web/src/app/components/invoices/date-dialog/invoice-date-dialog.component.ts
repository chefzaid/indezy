import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

export interface InvoiceDateDialogData {
  titleKey: string;
  messageKey: string;
  messageParams?: Record<string, unknown>;
  labelKey: string;
  confirmKey: string;
  /** The date offered, as YYYY-MM-DD. */
  date: string;
  /** The latest date allowed, as YYYY-MM-DD. */
  max?: string;
}

/** Asks for one date (when an invoice is issued, when it was paid); closes with it, or with nothing when cancelled. */
@Component({
  selector: 'app-invoice-date-dialog',
  imports: [FormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ data.titleKey | translate }}</h2>
    <mat-dialog-content>
      <p>{{ data.messageKey | translate: data.messageParams }}</p>
      <mat-form-field appearance="outline">
        <mat-label>{{ data.labelKey | translate }}</mat-label>
        <input matInput type="date" [(ngModel)]="date" [max]="data.max ?? ''" required data-invoice-date>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="undefined">{{ 'common.cancel' | translate }}</button>
      <button mat-raised-button color="primary" type="button" [disabled]="!date || (!!data.max && date > data.max)"
        [mat-dialog-close]="date" data-invoice-date-confirm>{{ data.confirmKey | translate }}</button>
    </mat-dialog-actions>
  `,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class InvoiceDateDialogComponent {
  readonly data = inject<InvoiceDateDialogData>(MAT_DIALOG_DATA);
  date = this.data.date;
}
