import { LegalForm } from './billing.models';

/** What a business expense paid for. */
export type ExpenseCategory = 'TRAVEL' | 'MEALS' | 'EQUIPMENT' | 'SOFTWARE' | 'TRAINING' | 'OFFICE' | 'INSURANCE' | 'FEES' | 'OTHER';

export const EXPENSE_CATEGORIES: ExpenseCategory[] = ['TRAVEL', 'MEALS', 'EQUIPMENT', 'SOFTWARE', 'TRAINING', 'OFFICE', 'INSURANCE', 'FEES', 'OTHER'];

/** What expenses change in the net income, by legal status. */
export type ExpenseTreatment = 'FLAT_ALLOWANCE' | 'DEDUCTIBLE' | 'REIMBURSED' | 'RECORDED';

export interface Expense {
  id: number;
  spentOn: string;
  label: string;
  category: ExpenseCategory;
  /** What was paid, VAT included. */
  amount: number;
  vat: number;
  projectId: number | null;
  projectRole: string | null;
  notes: string | null;
}

export interface ExpenseDraft {
  spentOn: string;
  label: string;
  category: ExpenseCategory;
  amount: number;
  vat: number | null;
  projectId: number | null;
  notes: string | null;
}

/** A year's expenses and what they change in the net income under the workspace's status. */
export interface ExpenseSummary {
  year: number;
  legalForm: LegalForm | null;
  treatment: ExpenseTreatment;
  total: number;
  vat: number;
  /** What lowers the taxable profit or is reimbursed. */
  deductible: number;
  /** What they cost once charges and tax are counted. */
  netCost: number;
  reversionRate: number;
  months: number[];
  byCategory: Partial<Record<ExpenseCategory, number>>;
}
