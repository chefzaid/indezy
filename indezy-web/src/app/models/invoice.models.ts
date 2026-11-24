import { ActivityReportStatus, DocumentLanguage } from './activity-report.models';

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'CREDITED';

/** An invoice, a credit note (avoir) correcting or cancelling an issued one, or the deposit invoice (facture d'acompte) of a quote. */
export type InvoiceKind = 'INVOICE' | 'CREDIT_NOTE' | 'DEPOSIT';

/** A line: days (or any quantity) at a price before VAT, with its VAT rate in percent; a negative quantity takes off. */
export interface InvoiceLine {
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
}

/** The currencies an invoice can be written in; another than the euro needs its rate at the issue date. */
export type InvoiceCurrency = 'EUR' | 'USD' | 'GBP' | 'CHF';

export const INVOICE_CURRENCIES: InvoiceCurrency[] = ['EUR', 'USD', 'GBP', 'CHF'];

export const CURRENCY_SYMBOLS: Record<InvoiceCurrency, string> = { EUR: '€', USD: '$', GBP: '£', CHF: 'CHF' };

/** What an overdue invoice is owed on top of its amount: interest at the rate it stated (null without a figure) and €40. */
export interface LateFees {
  daysLate: number;
  amountDue: number;
  rate: number | null;
  interest: number | null;
  indemnity: number;
}

export interface Invoice {
  id: number;
  kind: InvoiceKind;
  /** The invoice a credit note credits, and its number. */
  creditedInvoiceId: number | null;
  creditedNumber: string | null;
  /** The accepted quote the invoice bills, and its number. */
  quoteId?: number | null;
  quoteNumber?: string | null;
  /** Given when issued: "F2026-0007". */
  number: string | null;
  status: InvoiceStatus;
  /** Issued and past its due date. */
  overdue: boolean;
  clientId: number;
  clientName: string | null;
  projectId: number | null;
  projectRole: string | null;
  activityReportId: number | null;
  issueDate: string | null;
  dueDate: string | null;
  periodStart: string | null;
  periodEnd: string | null;
  language: DocumentLanguage;
  notes: string | null;
  lines: InvoiceLine[];
  totalExcludingVat: number;
  vat: number;
  total: number;
  paidOn: string | null;
  /** Set while the invoice is overdue. */
  lateFees?: LateFees | null;
  /** The currency of the lines and totals: the euro when absent. */
  currency?: InvoiceCurrency;
  /** Euros for one unit of the currency, at the issue date; null in euros. */
  exchangeRate?: number | null;
  /** The totals in euros: null while an invoice in another currency has no rate. */
  totalExcludingVatInEuros?: number | null;
  totalInEuros?: number | null;
  createdAt: string;
}

/** A draft written by hand. */
export interface InvoiceDraft {
  clientId: number;
  projectId?: number | null;
  periodStart?: string | null;
  periodEnd?: string | null;
  language: DocumentLanguage;
  notes?: string | null;
  lines: InvoiceLine[];
  currency?: InvoiceCurrency;
  exchangeRate?: number | null;
}

/** A month's activity report that can be invoiced: days worked, not rejected, not invoiced yet. */
export interface InvoiceableReport {
  reportId: number;
  projectId: number;
  projectRole: string;
  /** Whom the month bills: the mission's ESN, else its client; null without one. */
  clientName: string | null;
  year: number;
  month: number;
  status: ActivityReportStatus;
  workedDays: number;
  /** The mission's daily rate and the amount before VAT it gives the days worked; null without a rate. */
  dailyRate: number | null;
  amount: number | null;
}

/** How a client pays: over how many paid invoices, the average days from issue to payment and past the due date. */
export interface PaymentDelay {
  clientId: number;
  paidInvoices: number;
  averageDays: number;
  averageDaysLate: number;
}

/** Where the revenue stands against a ceiling: under it, from 80 % of it, past it once (micro-entreprise), or ending on a day. */
export type CeilingLevel = 'UNDER' | 'NEAR' | 'OVER' | 'ENDS';

/** One ceiling: its amount (and the VAT franchise's higher threshold), the revenue measured, its share, and when the regime or franchise ends. */
export interface Ceiling {
  limit: number;
  tolerance: number | null;
  revenue: number;
  percent: number;
  level: CeilingLevel;
  endsOn: string | null;
}

/** The year's revenue received (excluding VAT) against the micro-entreprise ceiling and the VAT franchise, each null when it does not apply. */
export interface RevenueCeilings {
  year: number;
  figuresYear: number;
  legalForm: string | null;
  vatExempt: boolean;
  received: number;
  receivedLastYear: number;
  outstanding: number;
  micro: Ceiling | null;
  vat: Ceiling | null;
}

/** Where a declaration stands: its period still running, ended and to declare, or past its deadline. */
export type DeclarationState = 'RUNNING' | 'TO_DECLARE' | 'PAST';

/** One month or quarter to declare: its days, the revenue received in it, the contributions on it (null without a rate) and their deadline. */
export interface ContributionPeriod {
  from: string;
  to: string;
  revenue: number;
  contributions: number | null;
  dueOn: string;
  state: DeclarationState;
}

/** The year's URSSAF contributions: the rate applied (null when none is known) and whether it is the official one, and the declarations of a micro-entreprise. */
export interface ContributionEstimate {
  year: number;
  figuresYear: number;
  legalForm: string | null;
  activity: 'BNC_SSI' | 'BNC_CIPAV' | 'BIC_SERVICES';
  incomeTaxDischarge: boolean;
  frequency: 'MONTHLY' | 'QUARTERLY';
  rate: number | null;
  officialRate: boolean;
  revenue: number;
  contributions: number | null;
  periods: ContributionPeriod[];
  /** For an EI under the real regime: the year's profit so far, the base after the 26 % abatement, and the contributions line by line. */
  profit?: number | null;
  base?: number | null;
  lines?: ContributionLine[];
}

/** One of an EI's contributions under the real regime. */
export interface ContributionLine {
  kind: 'HEALTH' | 'DAILY_ALLOWANCES' | 'BASE_PENSION' | 'SUPPLEMENTARY_PENSION' | 'DISABILITY' | 'FAMILY' | 'CSG_CRDS' | 'TRAINING';
  amount: number;
}

/** A tax deadline of the next twelve months: the CFE, a VAT instalment (its month only known) or the annual VAT return. */
export interface TaxDeadline {
  kind: 'CFE' | 'INCOME_TAX_INSTALMENT' | 'VAT_INSTALMENT' | 'VAT_RETURN' | 'VAT_PERIOD_RETURN';
  dueOn: string;
  monthOnly: boolean;
  /** Estimated from the VAT on the payments received; null when it cannot be (the CFE, a period not begun). */
  amount: number | null;
  /** For a normal-regime return (CA3): the first month of its period ("2026-11") and its length, 1 or 3 months. */
  covers?: string | null;
  months?: number | null;
}

/** Why a bank credit was matched to an invoice, the strongest first: its number in the label, its client's name, the amount alone. */
export type BankMatchReason = 'NUMBER' | 'CLIENT' | 'AMOUNT';

/** A credit of a bank statement, with the invoice awaiting payment it pays when one is found. */
export interface BankMatch {
  date: string;
  label: string;
  amount: number;
  invoiceId: number | null;
  invoiceNumber: string | null;
  clientName: string | null;
  reason: BankMatchReason | null;
}
