import { LostReason } from './project.models';

/** A won mission's share of the year; `reported` when its days come from activity reports, else estimated. */
export interface YearlyReviewMission {
  projectId: number;
  role: string;
  clientName?: string;
  dailyRate?: number;
  start: string;
  end?: string;
  days: number;
  reported: boolean;
  revenue?: number;
}

/** Revenue (€), days worked, the average daily rate they make and the number of missions. */
export interface YearlyReviewTotals {
  revenue: number;
  days: number;
  averageDailyRate?: number;
  missions: number;
}

export interface YearlyReviewPipeline {
  opened: number;
  won: number;
  lost: number;
  conversionRate?: number;
  lostReasons: { reason: LostReason; count: number }[];
}

/** A freelancer's year: missions, revenue against the year before, opportunities, skills asked for and network. */
export interface YearlyReview {
  year: number;
  name: string;
  missions: YearlyReviewMission[];
  totals: YearlyReviewTotals;
  previousYear: YearlyReviewTotals;
  pipeline: YearlyReviewPipeline;
  topSkills: { skill: string; count: number }[];
  network: { clients: number; esns: number; contacts: number; exchanges: number };
}
