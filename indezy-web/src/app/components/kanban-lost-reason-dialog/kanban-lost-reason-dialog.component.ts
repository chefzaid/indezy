import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { LostReason, LOST_REASONS } from '../../models/project.models';

export interface KanbanLostReasonDialogData {
  role: string;
}

@Component({
  selector: 'app-kanban-lost-reason-dialog',
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    TranslateModule
  ],
  templateUrl: './kanban-lost-reason-dialog.component.html',
  styleUrls: ['./kanban-lost-reason-dialog.component.scss']
})
export class KanbanLostReasonDialogComponent {
  readonly lostReasons = LOST_REASONS;
  reasonForm: FormGroup;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<KanbanLostReasonDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: KanbanLostReasonDialogData
  ) {
    this.reasonForm = this.fb.group({
      lostReason: ['', Validators.required]
    });
  }

  onConfirm(): void {
    if (this.reasonForm.valid) {
      this.dialogRef.close(this.reasonForm.value.lostReason as LostReason);
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
