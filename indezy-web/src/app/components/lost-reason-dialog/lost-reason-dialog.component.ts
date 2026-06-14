import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { LostReason, LOST_REASONS } from '../../models/project.models';

export interface LostReasonDialogData {
  role: string;
}

@Component({
  selector: 'app-lost-reason-dialog',
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    TranslateModule
  ],
  templateUrl: './lost-reason-dialog.component.html',
  styleUrls: ['./lost-reason-dialog.component.scss']
})
export class LostReasonDialogComponent {
  readonly reasons = LOST_REASONS;
  selectedReason: LostReason | null = null;

  constructor(
    private readonly dialogRef: MatDialogRef<LostReasonDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: LostReasonDialogData
  ) {}

  confirm(): void {
    // Close with a result object so the caller can distinguish confirm (even with no reason) from cancel.
    this.dialogRef.close({ reason: this.selectedReason ?? undefined });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
