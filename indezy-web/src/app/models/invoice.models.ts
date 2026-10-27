import { DocumentLanguage } from './activity-report.models';

export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID';

/** A line: days (or any quantity) at a price before VAT, with its VAT rate in percent. */
export interface InvoiceLine {
  description: string;
  quantity: number;
  unitPrice: number;
  vatRate: number;
}

export interface Invoice {
  id: number;
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
