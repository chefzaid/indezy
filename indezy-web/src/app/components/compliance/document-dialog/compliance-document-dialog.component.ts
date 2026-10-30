import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import {
  COMPLIANCE_DOCUMENT_TYPES, COMPLIANCE_VALIDITY_MONTHS, ComplianceDetails, ComplianceDocument, ComplianceDocumentType
} from '../../../models';

export interface ComplianceDocumentDialogData {
  /** The document to change; none to store a new one, which needs its file. */
  document?: ComplianceDocument;
}

export interface ComplianceDocumentDialogResult {
  file: File | null;
  details: ComplianceDetails;
}

const MAX_BYTES = 10 * 1024 * 1024;

/** Stores a document (a PDF or image of 10 MB at most) or changes what it is: type, label and dates. */
@Component({
  selector: 'app-compliance-document-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslateModule],
  templateUrl: './compliance-document-dialog.component.html',
  styleUrls: ['./compliance-document-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ComplianceDocumentDialogComponent {
  readonly data = inject<ComplianceDocumentDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ComplianceDocumentDialogComponent, ComplianceDocumentDialogResult>);
  readonly types = COMPLIANCE_DOCUMENT_TYPES;

  file: File | null = null;
  fileError: 'required' | 'tooLarge' | 'type' | null = null;

  readonly form = inject(FormBuilder).group({
    type: [this.data.document?.type ?? 'URSSAF_VIGILANCE' as ComplianceDocumentType, Validators.required],
    label: [this.data.document?.label ?? '', Validators.maxLength(100)],
    issuedOn: [this.data.document?.issuedOn ?? ''],
    expiresOn: [this.data.document?.expiresOn ?? '']
  }, { validators: control => this.check(control) });

  /** Months the server counts from the issue date when no end is given, for the hint. */
  get validityMonths(): number | undefined {
    return COMPLIANCE_VALIDITY_MONTHS[this.form.controls.type.value!];
  }

  pick(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.file = file;
    if (!file) {
      this.fileError = 'required';
    } else if (file.size > MAX_BYTES) {
      this.fileError = 'tooLarge';
    } else if (!/\.(pdf|png|jpe?g)$/i.test(file.name)) {
      this.fileError = 'type';
    } else {
      this.fileError = null;
    }
  }

  save(): void {
    if (!this.data.document && !this.file) {
      this.fileError = 'required';
    }
    if (this.form.invalid || this.fileError) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({
      file: this.file,
      details: { type: value.type!, label: value.label?.trim() || null, issuedOn: value.issuedOn || null, expiresOn: value.expiresOn || null }
    });
  }

  /** Another document needs a name, and none expires before it was issued. */
  private check(control: AbstractControl): ValidationErrors | null {
    const { type, label, issuedOn, expiresOn } = control.value;
    if (type === 'OTHER' && !label?.trim()) {
      return { labelRequired: true };
    }
    return issuedOn && expiresOn && expiresOn < issuedOn ? { expiresBeforeIssue: true } : null;
  }
}
