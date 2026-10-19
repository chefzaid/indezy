// Monthly activity reports (CRA) of accepted missions

export type ActivityReportStatus = 'DRAFT' | 'SUBMITTED' | 'VALIDATED' | 'REJECTED';

export type ActivityDayType = 'ONSITE' | 'REMOTE' | 'ABSENCE';

export type ActivityReportLayout = 'DAILY_TABLE' | 'CALENDAR_GRID';

export type DocumentLanguage = 'FR' | 'EN';

export type PublicHolidayName =
  | 'NEW_YEAR' | 'EASTER_MONDAY' | 'LABOUR_DAY' | 'VICTORY_DAY' | 'ASCENSION' | 'WHIT_MONDAY'
  | 'BASTILLE_DAY' | 'ASSUMPTION' | 'ALL_SAINTS' | 'ARMISTICE' | 'CHRISTMAS';

/** Status pill colors, dark enough to carry white text. */
export const ACTIVITY_REPORT_STATUS_COLORS: Record<ActivityReportStatus, string> = {
  DRAFT: '#546e7a',
  SUBMITTED: '#ef6c00',
  VALIDATED: '#2e7d32',
  REJECTED: '#d32f2f'
};

/** One recorded day; days without an entry were not worked. Dates are ISO `yyyy-MM-dd`. */
export interface ActivityReportDay {
  date: string;
  type: ActivityDayType;
  halfDay: boolean;
}

export interface PublicHoliday {
  date: string;
  holiday: PublicHolidayName;
}

export interface ActivityReport {
  /** Absent until the month is saved for the first time. */
  id?: number;
  projectId?: number;
  year: number;
  month: number;
  status: ActivityReportStatus;
  comment?: string;
  days: ActivityReportDay[];
  publicHolidays: PublicHoliday[];
  businessDays: number;
  workedDays: number;
  onsiteDays: number;
  remoteDays: number;
  absenceDays: number;
  dailyRate?: number;
  /** Worked days × daily rate, excluding VAT. */
  amount?: number;
  missionStart?: string;
  missionEnd?: string;
  submittedAt?: string;
  linkExpiresAt?: string;
  validatorName?: string;
  decidedAt?: string;
  decisionComment?: string;
}

export interface ActivityReportUpdate {
  comment?: string;
  days: ActivityReportDay[];
}

/** A new validation link: the token is shown once, the server keeps only its hash. */
export interface ActivityReportLink {
  token: string;
  expiresAt: string;
  report: ActivityReport;
}

/** What the client or ESN sees through the validation link. */
export interface PublicActivityReport {
  consultantName: string;
  clientName: string;
  middlemanName?: string;
  missionRole: string;
  showsLocation: boolean;
  report: ActivityReport;
}

export interface ActivityReportDecision {
  approved: boolean;
  validatorName: string;
  comment?: string;
}
