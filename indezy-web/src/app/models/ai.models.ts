import { WorkMode } from './project.models';

/** Whether AI can be used, through whose key, and this month's allowance in US dollars. */
export interface AiStatus {
  available: boolean;
  /** Why not: no key on the server, the account's or everyone's allowance used up. */
  reason: 'NO_KEY' | 'ACCOUNT_LIMIT' | 'MONTHLY_LIMIT' | null;
  ownKey: boolean;
  model: string;
  spentUsd: number;
  limitUsd: number;
}

export type AiModel = 'claude-opus-5-5' | 'claude-sonnet-5-5' | 'claude-haiku-5-5';

export const AI_MODELS: AiModel[] = ['claude-opus-5-5', 'claude-sonnet-5-5', 'claude-haiku-5-5'];

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

export interface AiMessageDraft {
  subject: string;
  body: string;
}
