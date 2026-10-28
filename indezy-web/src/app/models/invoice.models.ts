import { ActivityReportStatus, DocumentLanguage } from './activity-report.models';

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'CREDITED';

/** An invoice, or a credit note (avoir) correcting or cancelling an issued one. */
export type InvoiceKind = 'INVOICE' | 'CREDIT_NOTE';

/** A line: days (or any quantity) at a price before VAT, with its VAT rate in percent. */
export interface InvoiceLine {
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
}

export interface Invoice {
  id: number;
  kind: InvoiceKind;
  /** The invoice a credit note credits, and its number. */
  creditedInvoiceId: number | null;
  creditedNumber: string | null;
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
