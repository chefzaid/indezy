/** How a freelancer's business is set up, which decides what its invoices must state. */
export type LegalForm = 'MICRO_ENTREPRISE' | 'EI' | 'EURL' | 'SASU' | 'SARL' | 'SAS' | 'PORTAGE' | 'OTHER';

export const LEGAL_FORMS: LegalForm[] = ['MICRO_ENTREPRISE', 'EI', 'EURL', 'SASU', 'SARL', 'SAS', 'PORTAGE', 'OTHER'];

/** What the workspace's invoices say of the seller. */
/** What a Word template lays out. */
export type DocumentTemplateKind = 'INVOICE' | 'QUOTE';

/** A Word template kept for the invoices or the quotes, with the placeholders it uses, those unknown and the mentions it lacks. */
export interface DocumentTemplate {
  kind: DocumentTemplateKind;
  fileName: string;
  uploadedAt: string;
  found: string[];
  unknown: string[];
  missing: string[];
}

/** A block of an invoice's or a quote's body, under the seller, the heading and the client. */
export type BodyBlock = 'LINES' | 'MENTIONS' | 'NOTES' | 'SIGNATURE';

/** How a text block is drawn: its size, its alignment, a frame around it and a grey shading under it. */
export interface BlockStyle {
  size: 'SMALL' | 'NORMAL' | 'LARGE';
  align: 'LEFT' | 'CENTER' | 'RIGHT';
  framed: boolean;
  shaded: boolean;
  /** The words' colour: the text's, the accent colour, or grey. */
  color: 'TEXT' | 'ACCENT' | 'GREY';
  bold: boolean;
}

/** The blocks a style applies to, and the style they have without one. */
export const STYLED_BLOCKS: BodyBlock[] = ['MENTIONS', 'NOTES'];
export const USUAL_BLOCK_STYLE: BlockStyle = { size: 'SMALL', align: 'LEFT', framed: false, shaded: false, color: 'TEXT', bold: false };

/** The order the documents print in when none was chosen. */
export const USUAL_BODY_BLOCKS: BodyBlock[] = ['LINES', 'MENTIONS', 'NOTES', 'SIGNATURE'];

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
  /** The footer's centre and right zones and the header of the pages after the first, with placeholders; null for none. */
  footerCenter?: string | null;
  footerRight?: string | null;
  pageHeaderLeft?: string | null;
  pageHeaderRight?: string | null;
  /** Read-only: the terms and conditions PDF appended after each quote; null for none. Set with its own upload. */
  quoteTermsFileId?: number | null;
  /** The documents' font: a bundled open-licence one, or the uploaded one (CUSTOM). */
  font?: 'SANS' | 'SERIF' | 'CUSTOM';
  /** The layout of the documents: simple, modern (a coloured band) or classic (serif, rules and frames). */
  theme?: 'SIMPLE' | 'MODERN' | 'CLASSIC';
  /** Read-only: the uploaded font and its family; null for none. Set with its own upload. */
  fontFileId?: number | null;
  /** Read-only: the letterhead PDF the documents are drawn over; null for none. Set with its own upload. */
  letterheadId?: number | null;
  /** The room the letterhead's header and footer take, in millimetres, kept free of the document; null for the usual margins. */
  letterheadTop?: number | null;
  letterheadBottom?: number | null;
  /** The quotes' own accent colour and layout; null for the invoices' ones. */
  quoteAccentColor?: string | null;
  quoteTheme?: 'SIMPLE' | 'MODERN' | 'CLASSIC' | null;
  /** The order of the body's blocks; the lines, mentions and notes always print, the signature box (quotes) only when listed. */
  bodyBlocks?: BodyBlock[];
  /** The mentions' and the notes' own styles; the others keep the layout's. */
  blockStyles?: Partial<Record<BodyBlock, BlockStyle>>;
  fontFamily?: string | null;
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
