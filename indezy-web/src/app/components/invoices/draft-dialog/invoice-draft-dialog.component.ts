import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { ClientDto, DocumentLanguage, Invoice, InvoiceDraft, InvoiceLine, ProjectDto } from '../../../models';

export interface InvoiceDraftDialogData {
  clients: ClientDto[];
  projects: ProjectDto[];
  /** The draft to change; none for a new one. */
  invoice?: Invoice;
  /** The VAT rate new lines start with: 0 when exempt. */
  vatRate: number;
}

const MAX_LINES = 50;

/** Writes a draft: the client, optionally its mission, the period, the language, notes and lines, with live totals. */
@Component({
  selector: 'app-invoice-draft-dialog',
  imports: [ReactiveFormsModule, DecimalPipe, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  templateUrl: './invoice-draft-dialog.component.html',
  styleUrls: ['./invoice-draft-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class InvoiceDraftDialogComponent implements OnInit {
  readonly data = inject<InvoiceDraftDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<InvoiceDraftDialogComponent, InvoiceDraft>);
  private readonly fb = inject(FormBuilder);
  readonly languages: DocumentLanguage[] = ['FR', 'EN'];

  readonly form = this.fb.group({
    clientId: [null as number | null, Validators.required],
    projectId: [null as number | null],
    periodStart: [''],
    periodEnd: [''],
    language: ['FR' as DocumentLanguage],
    notes: ['', Validators.maxLength(1000)],
    lines: this.fb.array<FormGroup>([])
  });

  get lines(): FormArray<FormGroup> {
    return this.form.controls.lines;
  }

  get canAddLine(): boolean {
    return this.lines.length < MAX_LINES;
  }

  /** The missions of the chosen client, as client or as ESN. */
  get projects(): ProjectDto[] {
    const clientId = this.form.controls.clientId.value;
    return this.data.projects.filter(project => project.clientId === clientId || project.middlemanId === clientId);
  }

  ngOnInit(): void {
    const invoice = this.data.invoice;
    if (invoice) {
      this.form.patchValue({
        clientId: invoice.clientId, projectId: invoice.projectId, periodStart: invoice.periodStart ?? '',
        periodEnd: invoice.periodEnd ?? '', language: invoice.language, notes: invoice.notes ?? ''
      });
      invoice.lines.forEach(line => this.addLine(line));
      if (invoice.kind === 'CREDIT_NOTE') {
        // A credit note goes to the client of its invoice.
        this.form.controls.clientId.disable();
      }
    } else {
      this.addLine();
    }
  }

  addLine(line?: InvoiceLine): void {
    this.lines.push(this.fb.group({
      description: [line?.description ?? '', [Validators.required, Validators.maxLength(300)]],
      quantity: [line?.quantity ?? 1, [Validators.required, Validators.min(0.01), Validators.max(9999)]],
      unitPrice: [line?.unitPrice ?? null, [Validators.required, Validators.min(0), Validators.max(999999)]],
      vatRate: [line?.vatRate ?? this.data.vatRate, [Validators.required, Validators.min(0), Validators.max(30)]]
    }));
  }

  removeLine(index: number): void {
    this.lines.removeAt(index);
  }

  /** Each line to the cent, its VAT to the cent, then summed, as the server counts. */
  get totals(): { excludingVat: number; vat: number; total: number } {
    let excludingVat = 0;
    let vat = 0;
    for (const line of this.lines.getRawValue() as InvoiceLine[]) {
      const amount = Math.round((Number(line.quantity) || 0) * (Number(line.unitPrice) || 0) * 100) / 100;
      excludingVat += amount;
      vat += Math.round(amount * (Number(line.vatRate) || 0)) / 100;
    }
    return { excludingVat, vat, total: excludingVat + vat };
  }

  clientChanged(): void {
    if (!this.projects.some(project => project.id === this.form.controls.projectId.value)) {
      this.form.controls.projectId.setValue(null);
    }
  }

  save(): void {
    if (this.form.invalid || this.lines.length === 0) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({
      clientId: value.clientId!,
      projectId: value.projectId,
      periodStart: value.periodStart || null,
      periodEnd: value.periodEnd || null,
      language: value.language ?? 'FR',
      notes: value.notes?.trim() || null,
      lines: (value.lines as InvoiceLine[]).map(line => ({
        description: line.description.trim(), quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), vatRate: Number(line.vatRate)
      }))
    });
  }
}
