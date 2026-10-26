// Contact related interfaces and types

export interface ContactDto {
  id?: number;
  firstName: string;
  lastName?: string;
  email?: string;
  phone?: string;
  clientId: number;
  clientName?: string;
  freelanceId?: number;
  /** Whom the contact reports to, in the same workspace; null for nobody. */
  managerId?: number | null;
  managerName?: string;
  fullName?: string;
  notes?: string;
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
