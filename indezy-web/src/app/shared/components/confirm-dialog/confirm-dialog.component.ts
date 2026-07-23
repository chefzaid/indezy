import { Component, Inject } from '@angular/core';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TranslateModule } from '@ngx-translate/core';

export interface ConfirmDialogData {
  /** Translation key for the dialog title. Defaults to `common.confirm`. */
  titleKey?: string;
  /** Translation key for the dialog message. */
  messageKey: string;
  /** Interpolation parameters for the message translation. */
  messageParams?: Record<string, unknown>;
  /** Translation key for the confirm button. Defaults to `common.confirm`. */
  confirmKey?: string;
  /** Translation key for the cancel button. Defaults to `common.cancel`. */
  cancelKey?: string;
  /** When true, styles the confirm button as a destructive (warn) action. */
  danger?: boolean;
}

/**
 * Reusable confirmation dialog that replaces native `confirm()` pop-ups with a
 * consistent Material dialog. Resolves to `true` when confirmed, `false` otherwise.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule, MatButtonModule, TranslateModule],
  templateUrl: './confirm-dialog.component.html',
  styleUrls: ['./confirm-dialog.component.scss']
})
export class ConfirmDialogComponent {
  constructor(
    private readonly dialogRef: MatDialogRef<ConfirmDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData
  ) {}

  onConfirm(): void {
    this.dialogRef.close(true);
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }
}
