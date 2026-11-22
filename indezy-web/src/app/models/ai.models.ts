import { WorkMode } from './project.models';
import { DocumentLanguage } from './activity-report.models';

/** Whether AI can be used, through whose key, and this month's allowance in US dollars. */
export interface AiStatus {
  available: boolean;
  /** Why not: no key on the server, the account's or everyone's allowance used up. */
  reason: 'NO_KEY' | 'ACCOUNT_LIMIT' | 'MONTHLY_LIMIT' | null;
  ownKey: boolean;
  /** The model chosen with the member's own key; null when each task uses its own. */
  model: AiModel | null;
  /** The model each task uses. */
  models: Partial<Record<AiTask, AiModel>>;
  spentUsd: number;
  limitUsd: number;
  /** The models the member chose for some tasks with their own key. */
  chosen?: Partial<Record<AiTask, AiModel>>;
}

export type AiModel = 'claude-opus-5-5' | 'claude-sonnet-5-5' | 'claude-haiku-5-5';

export const AI_MODELS: AiModel[] = ['claude-opus-5-5', 'claude-sonnet-5-5', 'claude-haiku-5-5'];

/** A daily rate suggested for an opportunity being written, with its range, why, and the market median (0 without one). */
export interface AiRateSuggestion {
  rate: number;
  low: number;
  high: number;
  reasons: string[];
  marketMedian: number;
}

/** What the form says of an opportunity, to suggest a rate for. */
export interface AiRateRequest {
  role: string | null;
  techStack: string | null;
  workMode: WorkMode | null;
  remoteDaysPerMonth: number | null;
  place: string | null;
  durationInMonths: number | null;
  description: string | null;
  language: DocumentLanguage;
}

/** The announcement of the next availability: a LinkedIn post, a network post, and the day they announce. */
export interface AiAvailability {
  linkedin: string;
  network: string;
  availableFrom: string;
}

/** A rate negotiation prepared: the counter-offer, the walk-away rate, why, the ESN's margin (null without its client rate). */
export interface AiNegotiation {
  counterOffer: number;
  walkAway: number;
  strategy: string;
  arguments: string[];
  marginEuros: number | null;
  marginPercent: number | null;
  marketMedian: number;
}

/** The next interview prepared: likely questions with hints, a two-minute pitch, and questions to ask. */
export interface AiInterview {
  technical: { question: string; hint: string }[];
  behavioural: { question: string; hint: string }[];
  pitch: string;
  toAsk: string[];
}

/** What the freelancer looks for, read into the feed's filters; null for what was not said. */
/** A CV read into the profile's fields; what it does not say is null. */
export interface AiProfileImport {
  firstName: string | null;
  lastName: string | null;
  phone: string | null;
  city: string | null;
  position: string | null;
  company: string | null;
  bio: string | null;
  website: string | null;
  linkedin: string | null;
  github: string | null;
  languages: string[];
  skills: { name: string; years: number }[];
}

export interface AiOfferBrief {
  skills: string[];
  rateMin: number | null;
  minRemoteDays: number | null;
  workModes: WorkMode[];
  regions: string[];
  startBefore: string | null;
  durationMin: number | null;
  q: string | null;
  avoid: string[];
}

/** Where an application goes. */
export type AiPackChannel = 'ESN_EMAIL' | 'MALT' | 'LINKEDIN';

/** An application prepared for a feed offer: the channel's message, screening answers and a rate to propose. */
export interface AiApplicationPack {
  subject: string;
  message: string;
  screening: { question: string; answer: string }[];
  rate: number;
  rateReasons: string[];
}

/** How to polish a message: spelling and grammar only, more formal, warmer, or shorter. */
export type AiPolishTone = 'KEEP' | 'FORMAL' | 'FRIENDLY' | 'SHORTER';

export const AI_POLISH_TONES: AiPolishTone[] = ['KEEP', 'FORMAL', 'FRIENDLY', 'SHORTER'];

/** What an AI request is for, each on its own model unless one is chosen. */
export type AiTask = 'POSTING' | 'MESSAGE' | 'FIT' | 'CHAT' | 'TRIAGE' | 'DOSSIER' | 'RATE' | 'NEGOTIATION' | 'INTERVIEW';

export const AI_TASKS: AiTask[] = ['POSTING', 'TRIAGE', 'MESSAGE', 'DOSSIER', 'RATE', 'FIT', 'NEGOTIATION', 'INTERVIEW', 'CHAT'];

/** A job posting read into an opportunity's fields: null where it says nothing. */
export interface AiPosting {
  role: string | null;
  company: string | null;
  companyIsIntermediary: boolean;
  endClient: string | null;
  dailyRate: number | null;
  techStack: string[];
  place: string | null;
  workMode: WorkMode | null;
  remoteDaysPerMonth: number | null;
  startDate: string | null;
  durationInMonths: number | null;
  summary: string | null;
}

export type AiMessageKind = 'APPLICATION' | 'FOLLOW_UP' | 'THANK_YOU' | 'DECLINE';

export const AI_MESSAGE_KINDS: AiMessageKind[] = ['APPLICATION', 'FOLLOW_UP', 'THANK_YOU', 'DECLINE'];

/** An opportunity weighed against the profile. */
export interface AiFit {
  score: number;
  verdict: string;
  pros: string[];
  cons: string[];
  softCriteria: string[];
  questions: string[];
  rateAdvice: string;
  /** 0 when the context allows no figure. */
  suggestedRate: number;
}

/** A turn of the AI conversation about an opportunity. */
export interface AiChatTurn {
  id: number;
  fromMember: boolean;
  text: string;
  createdAt: string;
}

/** What the AI made of a feed offer for the workspace. */
export interface AiOfferVerdict {
  offerId: number;
  goodFit: boolean;
  verdict: string;
  dealBreakers: string[];
  createdAt: string;
}

export interface AiMessageDraft {
  subject: string;
  body: string;
}
