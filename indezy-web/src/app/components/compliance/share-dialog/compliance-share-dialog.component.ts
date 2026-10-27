import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { ComplianceDocument, CompliancePackRequest } from '../../../models';

export interface ComplianceShareDialogData {
  documents: ComplianceDocument[];
}

/** Chooses who a link is for, which documents it shares (the expired ones left out at first) and for how long. */
@Component({
  selector: 'app-compliance-share-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslateModule],
  templateUrl: './compliance-share-dialog.component.html',
  styleUrls: ['./compliance-share-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ComplianceShareDialogComponent {
  readonly data = inject<ComplianceShareDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ComplianceShareDialogComponent, CompliancePackRequest>);
  readonly durations = [7, 14, 30];

  readonly selected = new Set(this.data.documents.filter(document => document.validity !== 'EXPIRED').map(document => document.id));
  readonly form = inject(FormBuilder).nonNullable.group({
    recipient: ['', [Validators.required, Validators.maxLength(120)]],
    days: [14]
  });
  noneChosen = false;

  toggle(id: number, checked: boolean): void {
    if (checked) {
      this.selected.add(id);
    } else {
      this.selected.delete(id);
    }
    this.noneChosen = this.selected.size === 0;
  }

  share(): void {
    this.noneChosen = this.selected.size === 0;
    if (this.form.invalid || this.noneChosen) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({ recipient: value.recipient.trim(), documentIds: [...this.selected], days: value.days });
  }
}
