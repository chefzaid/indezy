import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';

import { EXPENSE_CATEGORIES, Expense, ExpenseCategory, ExpenseDraft, ProjectDto } from '../../../models';

export interface ExpenseDialogData {
  projects: ProjectDto[];
  /** The expense to change; none for a new one. */
  expense?: Expense;
}

/** Writes an expense: when, what for, how much VAT included, its VAT, optionally its mission and notes. */
@Component({
  selector: 'app-expense-dialog',
  imports: [ReactiveFormsModule, MatButtonModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslateModule],
  templateUrl: './expense-dialog.component.html',
  styleUrls: ['./expense-dialog.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class ExpenseDialogComponent implements OnInit {
  readonly data = inject<ExpenseDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ExpenseDialogComponent, ExpenseDraft>);
  private readonly fb = inject(FormBuilder);
  readonly categories = EXPENSE_CATEGORIES;

  readonly form = this.fb.group({
    spentOn: [new Date().toLocaleDateString('sv'), Validators.required],
    label: ['', [Validators.required, Validators.maxLength(200)]],
    category: ['TRAVEL' as ExpenseCategory, Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01), Validators.max(999999.99)]],
    vat: [null as number | null, [Validators.min(0)]],
    projectId: [null as number | null],
    notes: ['', Validators.maxLength(500)]
  });

  ngOnInit(): void {
    const expense = this.data.expense;
    if (expense) {
      this.form.patchValue({ spentOn: expense.spentOn, label: expense.label, category: expense.category, amount: expense.amount,
        vat: expense.vat || null, projectId: expense.projectId, notes: expense.notes ?? '' });
    }
  }

  /** The VAT is part of the amount, never more. */
  get vatTooHigh(): boolean {
    const { amount, vat } = this.form.getRawValue();
    return amount !== null && vat !== null && Number(vat) > Number(amount);
  }

  save(): void {
    if (this.form.invalid || this.vatTooHigh) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.dialogRef.close({
      spentOn: value.spentOn!, label: value.label!.trim(), category: value.category!, amount: Number(value.amount),
      vat: value.vat === null || value.vat === undefined ? null : Number(value.vat), projectId: value.projectId ?? null,
      notes: value.notes?.trim() || null
    });
  }
}
