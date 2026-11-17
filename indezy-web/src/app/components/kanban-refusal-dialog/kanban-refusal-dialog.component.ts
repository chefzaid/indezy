import { Component, Inject, ChangeDetectionStrategy } from '@angular/core';

import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

import { LostReason, LOST_REASONS, PROJECT_STATUS_ICONS, ProjectStatus, Refusal } from '../../models/project.models';

export interface KanbanRefusalDialogData {
  role: string;
  /** Abandoned by the recruiter (LOST) or withdrawn by the freelance (WITHDRAWN): who refused is the status. */
  status: ProjectStatus.LOST | ProjectStatus.WITHDRAWN;
}

/** Asks why an opportunity was abandoned or withdrawn, if they want to say; closes with the refusal. */
@Component({
  selector: 'app-kanban-refusal-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    TranslateModule
],
  templateUrl: './kanban-refusal-dialog.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrls: ['./kanban-refusal-dialog.component.scss']
})
export class KanbanRefusalDialogComponent {
  /**
   * Opens the dialog focused on itself, announced by its title: the stage menu's Enter that opens it must not reach
   * the reason select, which an Enter opens (and did on a busy machine, the dialog's first control holding the focus).
   */
  static open(dialog: MatDialog, data: KanbanRefusalDialogData): MatDialogRef<KanbanRefusalDialogComponent, Refusal> {
    return dialog.open(KanbanRefusalDialogComponent, { data, autoFocus: 'dialog' });
  }

  readonly icons = PROJECT_STATUS_ICONS;
  readonly lostReasons = LOST_REASONS;
  refusalForm: FormGroup;

  constructor(
    private readonly fb: FormBuilder,
    private readonly dialogRef: MatDialogRef<KanbanRefusalDialogComponent, Refusal>,
    @Inject(MAT_DIALOG_DATA) public data: KanbanRefusalDialogData
  ) {
    this.refusalForm = this.fb.group({
      lostReason: [null as LostReason | null]
    });
  }

  onConfirm(): void {
    const { lostReason } = this.refusalForm.value;
    this.dialogRef.close(lostReason ? { lostReason } : {});
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
