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
  /** Read-only: the logo printed on the documents; null for none. Set with its own upload. */
  logoId?: number | null;
  /** The accent colour of the documents' title, table header and rules, as #RRGGBB; null for black. */
  accentColor?: string | null;
  /** The terms and conditions of sale printed after each quote; null for none. */
  quoteTerms?: string | null;
  /** Read-only: whether invoices can be issued. */
  complete?: boolean;
}
