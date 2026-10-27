/** What a compliance document is; the order is the vault's. */
export type ComplianceDocumentType = 'URSSAF_VIGILANCE' | 'COMPANY_REGISTRATION' | 'PROFESSIONAL_INSURANCE' | 'BANK_DETAILS' | 'IDENTITY' |
  'OTHER';

export const COMPLIANCE_DOCUMENT_TYPES: ComplianceDocumentType[] = ['URSSAF_VIGILANCE', 'COMPANY_REGISTRATION', 'PROFESSIONAL_INSURANCE',
  'BANK_DETAILS', 'IDENTITY', 'OTHER'];

/** Months a document usually stays valid from its issue date, when the server sets the end itself. */
export const COMPLIANCE_VALIDITY_MONTHS: Partial<Record<ComplianceDocumentType, number>> = { URSSAF_VIGILANCE: 6, COMPANY_REGISTRATION: 3 };

/** Past its end, within 30 days of it, before it, or without one. */
export type ComplianceValidity = 'VALID' | 'EXPIRING' | 'EXPIRED' | 'NO_EXPIRY';

export interface ComplianceDocument {
  id: number;
  type: ComplianceDocumentType;
  label: string | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  issuedOn: string | null;
  expiresOn: string | null;
  validity: ComplianceValidity;
  createdAt: string;
}

export interface ComplianceDetails {
  type: ComplianceDocumentType;
  label: string | null;
  issuedOn: string | null;
  expiresOn: string | null;
}

/** Documents shared through one link. */
export interface CompliancePack {
  id: number;
  recipient: string;
  documentIds: number[];
  createdAt: string;
  expiresAt: string;
  revokedAt: string | null;
  active: boolean;
  openCount: number;
  lastOpenedAt: string | null;
}

export interface CompliancePackRequest {
  recipient: string;
  documentIds: number[];
  days: number;
}

/** A new pack and the token of its link, given this once. */
export interface CreatedCompliancePack {
  pack: CompliancePack;
  token: string;
}

/** What a shared link shows its recipient. */
export interface PublicCompliancePack {
  sharedBy: string;
  recipient: string;
  expiresAt: string;
  documents: PublicComplianceDocument[];
}

export interface PublicComplianceDocument {
  id: number;
  type: ComplianceDocumentType;
  label: string | null;
  fileName: string;
  issuedOn: string | null;
  expiresOn: string | null;
}

/** A document to renew among the dashboard's reminders; negative days once expired. */
export interface ExpiringDocument {
  documentId: number;
  type: ComplianceDocumentType;
  label: string | null;
  expiresOn: string;
  daysLeft: number;
}
