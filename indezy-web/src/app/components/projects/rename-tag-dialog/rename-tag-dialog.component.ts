import { Component, Inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

export interface RenameTagDialogData {
  tags: string[];
}

export interface RenameTagResult {
  from: string;
  to: string;
}

/**
 * Collects the tag to rename (from the current project's tags) and its new name.
 * Resolves with {@link RenameTagResult} when confirmed, or {@code undefined} when cancelled.
 */
@Component({
  selector: 'app-rename-tag-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './rename-tag-dialog.component.html',
  styleUrls: ['./rename-tag-dialog.component.scss']
})
export class RenameTagDialogComponent {
  from = '';
  to = '';

  constructor(
    private readonly dialogRef: MatDialogRef<RenameTagDialogComponent, RenameTagResult>,
    @Inject(MAT_DIALOG_DATA) public data: RenameTagDialogData
  ) {
    this.from = data.tags[0] ?? '';
  }

  onConfirm(): void {
    if (this.from && this.to.trim()) {
      this.dialogRef.close({ from: this.from, to: this.to.trim() });
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
