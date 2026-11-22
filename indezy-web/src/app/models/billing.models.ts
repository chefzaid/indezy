/** How a freelancer's business is set up, which decides what its invoices must state. */
export type LegalForm = 'MICRO_ENTREPRISE' | 'EI' | 'EURL' | 'SASU' | 'SARL' | 'SAS' | 'PORTAGE' | 'OTHER';

export const LEGAL_FORMS: LegalForm[] = ['MICRO_ENTREPRISE', 'EI', 'EURL', 'SASU', 'SARL', 'SAS', 'PORTAGE', 'OTHER'];

/** What the workspace's invoices say of the seller. */
export interface BillingIdentity {
  legalName: string | null;
  legalForm: LegalForm | null;
  siret: string | null;
  vatNumber: string | null;
  /** Exempt from VAT (franchise en base). */
  vatExempt: boolean;
  /** VAT rate in percent, when not exempt. */
  vatRate: number | null;
  shareCapital: number | null;
  /** "RCS Paris 123 456 789". */
  registration: string | null;
  address: string | null;
  iban: string | null;
  bic: string | null;
  paymentTermDays: number | null;
  invoicePrefix: string | null;
  /** The late-payment interest rate the terms state, in percent a year; null for the statutory rate without a figure. */
  latePaymentRate?: number | null;
  /** The notes a new invoice and a new quote start with; null for none. */
  invoiceNotes?: string | null;
  quoteNotes?: string | null;
  /** A line under the seller's name on the documents (a tagline, contact details); null for none. */
  header?: string | null;
  /** The free text at the foot of each page of the documents (website, phone); null for none. */
  footer?: string | null;
  /** Read-only: the terms and conditions PDF appended after each quote; null for none. Set with its own upload. */
  quoteTermsFileId?: number | null;
  /** Where the logo sits at the top of the documents, and how large. */
  logoPlacement?: 'LEFT' | 'CENTER' | 'RIGHT';
  logoSize?: 'SMALL' | 'MEDIUM' | 'LARGE';
  /** Read-only: the logo printed on the documents; null for none. Set with its own upload. */
  logoId?: number | null;
  /** The accent colour of the documents' title, table header and rules, as #RRGGBB; null for black. */
  accentColor?: string | null;
  /** The terms and conditions of sale printed after each quote; null for none. */
  quoteTerms?: string | null;
  /** Read-only: whether invoices can be issued. */
  complete?: boolean;
}
