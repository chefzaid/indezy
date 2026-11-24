// Contact related interfaces and types

/** Why the freelancer may keep a contact's data (GDPR article 6). */
export type ContactLegalBasis = 'LEGITIMATE_INTEREST' | 'CONSENT' | 'CONTRACT';
export const CONTACT_LEGAL_BASES: ContactLegalBasis[] = ['LEGITIMATE_INTEREST', 'CONSENT', 'CONTRACT'];

/** A contact's first and last names, as lists show them. */
export function contactName(contact: Pick<ContactDto, 'firstName' | 'lastName'>): string {
  return [contact.firstName, contact.lastName].filter(Boolean).join(' ');
}

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
  /** The contact asked not to be prospected: never offered in an announcement. */
  doNotContact?: boolean;
  /** When the contact was anonymized after the retention period; read-only. */
  anonymizedAt?: string | null;
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
  /** Read-only: set when Indezy sent the exchange as an email. */
  email?: SentEmail | null;
  /** Read-only: what an answer that came back through Indezy says, as the AI sorted it. */
  replyKind?: ReplyKind | null;
  /** Read-only: for a "not now" answer, the day it said to write back, when the follow-up comes due. */
  writeBackOn?: string | null;
}

/** What an answer says: interested, not now, no need, do not contact, or anything else. */
export type ReplyKind = 'INTERESTED' | 'NOT_NOW' | 'NO_NEED' | 'DO_NOT_CONTACT' | 'OTHER';

/** An exchange Indezy sent as an email: when, whether it tracks opening, and when and how often it was seen opened. */
export interface SentEmail {
  sentAt: string;
  tracked: boolean;
  openedAt?: string | null;
  opens: number;
}

/** Whether email can go from Indezy, and how many went for the workspace in the last 24 hours. */
export interface ContactEmailStatus {
  available: boolean;
  sentToday: number;
  dailyMax: number;
}

/** An email to a contact sent through Indezy. */
export interface ContactEmailRequest {
  projectId?: number | null;
  subject: string;
  body: string;
  track: boolean;
  copy: boolean;
  language: 'FR' | 'EN';
  /** Attaches the dossier de compétences. */
  dossier?: boolean;
  /** The prospection campaign it goes in. */
  campaignId?: number | null;
}

/** A prospection campaign and what came of it: emails sent and waiting, answers, opportunities opened since, missions won. */
export interface ProspectionCampaign {
  id: number;
  name: string;
  startedAt: string;
  sent: number;
  waiting: number;
  answered: number;
  /** Of those, the answers the AI sorted as interested. */
  interested?: number;
  opportunities: number;
  /** The interview steps of those opportunities: the calls booked. */
  interviews?: number;
  won: number;
  /** The contacts it wrote or plans to write to. */
  contactIds: number[];
}

/** Emails to send later from Indezy, spread over working days (9:00 to 17:00 in Paris, Monday to Friday). */
export interface ScheduledEmailPlan {
  emails: { contactId: number; subject: string; body: string }[];
  dossier: boolean;
  language: 'FR' | 'EN';
  /** Over how many working days, 1 to 10. */
  days: number;
  /** At most this many a day, 1 to 50. */
  dailyMax: number;
  campaignId?: number | null;
}

/** An email waiting to go; dropped unsent if the contact gets in touch first. */
export interface ScheduledEmail {
  id: number;
  contactId: number;
  name: string;
  company: string | null;
  subject: string;
  sendAt: string;
  /** A campaign's reminder, sent only to those who did not answer. */
  reminder?: boolean;
}

/** A contact of an ESN the workspace worked with or heard from, who has an address; those who answered most first. */
export interface AvailabilityRecipient {
  id: number;
  firstName: string | null;
  name: string;
  company: string;
  email: string;
  /** How many times they answered or wrote. */
  answers: number;
  lastContactedOn: string | null;
  /** How many missions naming a profile skill their company posted in the feed in the last 60 days. */
  postings: number;
  /** The title of the newest of those missions, for `{{mission}}`; null without any. */
  mission: string | null;
  /** How many earlier campaigns wrote to them without an answer. */
  silentCampaigns: number;
  /** How many opportunities opened with their company after a campaign wrote there. */
  campaignOpportunities: number;
  /** The best daily rate their company paid on a mission won through it; null without one. */
  ratePaid?: number | null;
  /** The last day of a mission won through their company ending within 12 weeks, which lists them first; null otherwise. */
  missionEndsOn?: string | null;
  /** Whether their ESN was worked with, answered before or posted missions for the profile; the others are listed last. */
  related?: boolean;
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
