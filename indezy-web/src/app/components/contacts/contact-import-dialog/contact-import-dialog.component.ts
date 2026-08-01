import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Collects a raw CSV or vCard payload to import contacts. Resolves with the pasted
 * content when confirmed, or {@code undefined} when cancelled.
 */
@Component({
  selector: 'app-contact-import-dialog',
  imports: [FormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslateModule],
  templateUrl: './contact-import-dialog.component.html',
  styleUrls: ['./contact-import-dialog.component.scss']
})
export class ContactImportDialogComponent {
  content = '';

  constructor(private readonly dialogRef: MatDialogRef<ContactImportDialogComponent, string>) {}

  onImport(): void {
    if (this.content.trim()) {
      this.dialogRef.close(this.content);
    }
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
