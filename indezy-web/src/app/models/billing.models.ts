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
  /** Read-only: whether invoices can be issued. */
  complete?: boolean;
}
