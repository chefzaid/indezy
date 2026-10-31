// User management related interfaces and types

export type SkillLevel = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';

/** What the profile says about one of its skills: a level, years of experience, or both. */
export interface SkillDetail {
  level?: SkillLevel;
  years?: number;
}

export interface UserProfile {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  birthDate?: string;
  address?: string;
  city?: string;
  avatar?: string;
  bio?: string;
  company?: string;
  position?: string;
  website?: string;
  linkedin?: string;
  github?: string;
  skills?: string[];
  /** A level and years of experience for some of the skills, keyed by skill. */
  skillDetails?: Record<string, SkillDetail>;
  languages?: string[];
  targetDailyRate?: number | null;
  /** Days to bill in a year, compared on the dashboard with the validated activity reports. */
  billedDaysTarget?: number | null;
  /** ISO day (yyyy-MM-dd) the workspace is next available from, which offers' start dates are matched against. */
  availabilityDate?: string | null;
  notifications?: UserNotificationSettings;
  preferences?: UserPreferences;
}

/** A section of the weekly digest, which the account can leave out. */
export type DigestSection = 'PIPELINE' | 'OVERDUE_INVOICES' | 'INTERVIEWS' | 'FOLLOW_UPS' | 'QUIET_OPPORTUNITIES' | 'MISSIONS_ENDING' | 'OFFERS';

/** The digest's sections in the order the email gives them. */
export const DIGEST_SECTIONS: DigestSection[] = ['PIPELINE', 'OVERDUE_INVOICES', 'INTERVIEWS', 'FOLLOW_UPS', 'QUIET_OPPORTUNITIES',
  'MISSIONS_ENDING', 'OFFERS'];

/** Which notifications the account gets, by event and channel. */
export interface UserNotificationSettings {
  /** The weekly digest, by email on Mondays. */
  weeklyReports: boolean;
  /** The digest's sections kept; the others are left out. */
  weeklyDigestSections: DigestSection[];
  /** An email for each call a recruiter books through the booking link. */
  bookingEmails: boolean;
  /** The count of the saved searches' new offers next to Offers in the menu. */
  newOffersBadge: boolean;
  /** Whether this deployment can send emails at all; read-only. */
  emailsAvailable: boolean;
}

export interface UserPreferences {
  theme: 'light' | 'dark' | 'auto';
  language: string;
  inactivityDays: number;
  /** Days a message sent to a contact waits for an answer before the dashboard says to follow up. */
  followUpDays?: number;
  /** Archive opportunities idle for the inactivity period, every night. */
  autoArchive?: boolean;
  /** Years without exchange after which contacts are anonymized every night; 0 for never. */
  contactRetentionYears?: number;
  /** The dashboard's blocks in the account's order, shown or hidden. */
  dashboardWidgets?: DashboardWidget[];
}

/** The blocks of the dashboard's overview. */
export type DashboardWidgetId = 'STATS' | 'CHARTS' | 'REMINDERS' | 'OFFERS' | 'ANALYTICS' | 'RECENT';

export interface DashboardWidget {
  id: DashboardWidgetId;
  visible: boolean;
}

export interface PasswordChangeRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export type WorkspaceImportKind = 'SOURCES' | 'CLIENTS' | 'CONTACTS' | 'SEASONS' | 'PROJECTS' | 'PROJECT_NOTES';

/** One record of an imported file: new, already in the workspace, or left out with why. */
export interface WorkspaceImportItem {
  label: string;
  status: 'NEW' | 'DUPLICATE' | 'INVALID';
  reason?: string;
}

export interface WorkspaceImportSection {
  kind: WorkspaceImportKind;
  total: number;
  added: number;
  duplicates: number;
  invalid: number;
  /** The first 200 records; the counts cover them all. */
  items: WorkspaceImportItem[];
}

/** What importing an account export adds to the workspace; `applied` is false for a preview. */
export interface WorkspaceImportReport {
  applied: boolean;
  sections: WorkspaceImportSection[];
}
