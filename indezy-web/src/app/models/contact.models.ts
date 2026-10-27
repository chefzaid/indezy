// Contact related interfaces and types

/** Why the freelancer may keep a contact's data (GDPR article 6). */
export type ContactLegalBasis = 'LEGITIMATE_INTEREST' | 'CONSENT' | 'CONTRACT';
export const CONTACT_LEGAL_BASES: ContactLegalBasis[] = ['LEGITIMATE_INTEREST', 'CONSENT', 'CONTRACT'];

export interface ContactDto {
  id?: number;
  firstName: string;
  /** Null clears a field emptied in the form. */
  lastName?: string | null;
  email?: string | null;
  phone?: string | null;
  clientId: number;
  clientName?: string;
  freelanceId?: number;
  /** Whom the contact reports to, in the same workspace; null for nobody. */
  managerId?: number | null;
  managerName?: string;
  fullName?: string;
  notes?: string | null;
  /** Why the freelancer keeps the contact's data; omitted, legitimate interest (or unchanged). */
  legalBasis?: ContactLegalBasis;
  /** The day (yyyy-MM-dd) of the latest recorded exchange; read-only. */
  lastContactedOn?: string;
  createdAt?: Date;
  updatedAt?: Date;
}

export type InteractionChannel = 'EMAIL' | 'PHONE' | 'LINKEDIN' | 'MEETING' | 'OTHER';
export type InteractionDirection = 'OUTGOING' | 'INCOMING';
export const INTERACTION_CHANNELS: InteractionChannel[] = ['EMAIL', 'PHONE', 'LINKEDIN', 'MEETING', 'OTHER'];

/** An exchange with a contact; {@code replied} only applies to one the freelancer started (null while waiting). */
export interface ContactInteraction {
  id?: number;
  contactId?: number;
  /** Read-only: whom the exchange was with, and the opportunity it was about. */
  contactName?: string;
  projectId?: number | null;
  projectRole?: string;
  occurredOn: string;
  channel: InteractionChannel;
  direction: InteractionDirection;
  replied?: boolean | null;
  summary?: string | null;
}

/** A contact's exchanges, newest first, and what they say about the relationship. */
export interface ContactInteractions {
  interactions: ContactInteraction[];
  lastContactedOn?: string | null;
  /** Exchanges the freelancer started whose answer is known, and those answered. */
  asked: number;
  answered: number;
  responseRate?: number | null;
  preferredChannel?: InteractionChannel | null;
}

/** Someone {@code contactId} works with, at their company (a client or an ESN), and how they work together. */
export interface ContactRelation {
  contactId: number;
  otherId: number;
  otherName: string;
  otherClientId?: number;
  otherClientName?: string;
  otherClientIsFinal?: boolean;
  note?: string | null;
}
