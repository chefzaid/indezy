import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AbstractControl, FormArray, FormBuilder, FormGroup, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { DecimalPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import {
  CURRENCY_SYMBOLS, ClientDto, DocumentLanguage, INVOICE_CURRENCIES, Invoice, InvoiceCurrency, InvoiceDraft, InvoiceLine, ProjectDto, Quote,
  QuoteDraft
} from '../../../models';

export interface InvoiceDraftDialogData {
  clients: ClientDto[];
  projects: ProjectDto[];
  /** The draft to change; none for a new one. */
  invoice?: Invoice;
  /** The VAT rate new lines start with: 0 when exempt. */
  vatRate: number;
  /** Writes a quote rather than an invoice: how long it holds and the deposit it asks, instead of a period. */
  forQuote?: boolean;
  /** The quote draft to change. */
  quote?: Quote;
}

const MAX_LINES = 50;

/** A quantity of zero bills nothing: a negative one takes off, as a discount or a deposit already invoiced. */
function nonZero(control: AbstractControl): ValidationErrors | null {
  return Number(control.value) === 0 ? { zero: true } : null;
}

/**
 * Writes a draft invoice or quote: the client, optionally its mission, the language, notes and lines, with live totals; an
 * invoice's period, or a quote's validity and deposit.
 */
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
  private readonly dialogRef = inject(MatDialogRef<InvoiceDraftDialogComponent, InvoiceDraft | QuoteDraft>);
  private readonly fb = inject(FormBuilder);
  readonly languages: DocumentLanguage[] = ['FR', 'EN'];
  readonly currencies = INVOICE_CURRENCIES;

  readonly form = this.fb.group({
    clientId: [null as number | null, Validators.required],
    projectId: [null as number | null],
    periodStart: [''],
    periodEnd: [''],
    language: ['FR' as DocumentLanguage],
    currency: ['EUR' as InvoiceCurrency],
    exchangeRate: [null as number | null, [Validators.min(0.000001), Validators.max(99999999)]],
    validityDays: [30, [Validators.required, Validators.min(1), Validators.max(365)]],
    depositPercent: [null as number | null, [Validators.min(1), Validators.max(100)]],
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

  /** The symbol amounts are written with: the invoice's currency, the euro for a quote. */
  get symbol(): string {
    return CURRENCY_SYMBOLS[this.form.controls.currency.value ?? 'EUR'];
  }

  get foreign(): boolean {
    return !this.data.forQuote && (this.form.controls.currency.value ?? 'EUR') !== 'EUR';
  }

  /** The total in euros at the rate typed, once there is one. */
  get totalInEuros(): number | null {
    const rate = Number(this.form.controls.exchangeRate.value);
    return this.foreign && rate > 0 ? Math.round(this.totals.total * rate * 100) / 100 : null;
  }

  get titleKey(): string {
    if (this.data.forQuote) {
      return this.data.quote ? 'quotes.editTitle' : 'quotes.newTitle';
    }
    return this.data.invoice?.kind === 'CREDIT_NOTE' ? 'invoices.editCreditNote' : this.data.invoice ? 'invoices.editTitle' : 'invoices.newTitle';
  }

  ngOnInit(): void {
    const invoice = this.data.invoice;
    const quote = this.data.quote;
    if (quote) {
      this.form.patchValue({
        clientId: quote.clientId, projectId: quote.projectId, language: quote.language, notes: quote.notes ?? '',
        validityDays: quote.validityDays, depositPercent: quote.depositPercent
      });
      quote.lines.forEach(line => this.addLine(line));
    } else if (invoice) {
      this.form.patchValue({
        clientId: invoice.clientId, projectId: invoice.projectId, periodStart: invoice.periodStart ?? '',
        periodEnd: invoice.periodEnd ?? '', language: invoice.language, notes: invoice.notes ?? '',
        currency: invoice.currency ?? 'EUR', exchangeRate: invoice.exchangeRate ?? null
      });
      invoice.lines.forEach(line => this.addLine(line));
      if (invoice.kind === 'CREDIT_NOTE') {
        // A credit note goes to the client of its invoice, in its currency and at its rate.
        this.form.controls.clientId.disable();
        this.form.controls.currency.disable();
        this.form.controls.exchangeRate.disable();
      }
    } else {
      this.addLine();
    }
  }

  addLine(line?: InvoiceLine): void {
    this.lines.push(this.fb.group({
      description: [line?.description ?? '', [Validators.required, Validators.maxLength(300)]],
      quantity: [line?.quantity ?? 1, [Validators.required, Validators.min(-9999), Validators.max(9999), nonZero]],
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
    const lines = (value.lines as InvoiceLine[]).map(line => ({
      description: line.description.trim(), quantity: Number(line.quantity), unitPrice: Number(line.unitPrice), vatRate: Number(line.vatRate)
    }));
    if (this.data.forQuote) {
      this.dialogRef.close({
        clientId: value.clientId!, projectId: value.projectId, language: value.language ?? 'FR', notes: value.notes?.trim() || null,
        validityDays: Number(value.validityDays), depositPercent: value.depositPercent ? Number(value.depositPercent) : null, lines
      });
      return;
    }
    this.dialogRef.close({
      clientId: value.clientId!,
      projectId: value.projectId,
      periodStart: value.periodStart || null,
      periodEnd: value.periodEnd || null,
      language: value.language ?? 'FR',
      notes: value.notes?.trim() || null,
      lines,
      currency: value.currency ?? 'EUR',
      exchangeRate: value.currency && value.currency !== 'EUR' && value.exchangeRate ? Number(value.exchangeRate) : null
    });
  }
}
