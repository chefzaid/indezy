import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

export interface SaveSearchDialogData {
  /** The name offered at first. */
  name: string;
}

/** Asks the name to save the feed's filters under; closes with it, or with nothing when cancelled. */
@Component({
  selector: 'app-save-search-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslateModule],
  templateUrl: './save-search-dialog.component.html',
  styleUrls: ['./save-search-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class SaveSearchDialogComponent {
  static readonly MAX_LENGTH = 80;
  readonly maxLength = SaveSearchDialogComponent.MAX_LENGTH;
  private readonly dialogRef = inject<MatDialogRef<SaveSearchDialogComponent, string>>(MatDialogRef);
  name = inject<SaveSearchDialogData>(MAT_DIALOG_DATA).name;

  save(): void {
    const name = this.name.trim();
    if (name) {
      this.dialogRef.close(name);
    }
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
