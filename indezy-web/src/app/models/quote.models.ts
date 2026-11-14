import { DocumentLanguage } from './activity-report.models';
import { InvoiceLine } from './invoice.models';

export type QuoteStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REFUSED';

/** A quote (devis) for fixed-price work; a sent one past its validity reads as expired. */
export interface Quote {
  id: number;
  /** Given when sent: "D2026-0003". */
  number: string | null;
  status: QuoteStatus;
  expired: boolean;
  clientId: number;
  clientName: string | null;
  projectId: number | null;
  projectRole: string | null;
  issueDate: string | null;
  validityDays: number;
  validUntil: string | null;
  /** The share of the total asked as a deposit on acceptance, in percent. */
  depositPercent: number | null;
  language: DocumentLanguage;
  notes: string | null;
  lines: InvoiceLine[];
  totalExcludingVat: number;
  vat: number;
  total: number;
  /** The day the client accepted or refused it. */
  decidedOn: string | null;
  /** The invoice billing it once accepted. */
  invoiceId: number | null;
  createdAt: string;
}

/** A draft: the client, optionally its mission, the language, how long it holds, the deposit asked, notes and lines. */
export interface QuoteDraft {
  clientId: number;
  projectId?: number | null;
  language: DocumentLanguage;
  validityDays?: number | null;
  depositPercent?: number | null;
  notes?: string | null;
  lines: InvoiceLine[];
}
