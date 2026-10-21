import { Component, Inject, ChangeDetectionStrategy } from '@angular/core';

import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
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
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    TranslateModule
],
  templateUrl: './kanban-lost-reason-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./kanban-lost-reason-dialog.component.scss']
})
export class KanbanLostReasonDialogComponent {
  /**
   * Opens the dialog focused on itself, announced by its title: the stage menu's Enter that opens it must not reach
   * the reason select, which an Enter opens (and did on a busy machine, the dialog's first control holding the focus).
   */
  static open(dialog: MatDialog, data: KanbanLostReasonDialogData): MatDialogRef<KanbanLostReasonDialogComponent, LostReason> {
    return dialog.open(KanbanLostReasonDialogComponent, { data, autoFocus: 'dialog' });
  }

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
