import { PurchaseOrderAlert } from './purchase-order.models';
import { ExpiringDocument } from './compliance.models';
// Project related interfaces and types

/** The pipeline stages in the order of the Kanban columns: a refused opportunity's column comes before the accepted one's. */
export enum ProjectStatus {
  CONTACT = 'CONTACT',
  INTERVIEW = 'INTERVIEW',
  OFFER = 'OFFER',
  LOST = 'LOST',
  WON = 'WON'
}

/** Pipeline stage colors, dark enough to carry white text in status pills (4.5:1 contrast at least). */
export const PROJECT_STATUS_COLORS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.CONTACT]: '#1565c0',
  [ProjectStatus.INTERVIEW]: '#b85600',
  [ProjectStatus.OFFER]: '#8e24aa',
  [ProjectStatus.LOST]: '#d32f2f',
  [ProjectStatus.WON]: '#2e7d32'
};

export const PROJECT_STATUS_ICONS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.CONTACT]: 'contact_mail',
  [ProjectStatus.INTERVIEW]: 'groups',
  [ProjectStatus.OFFER]: 'handshake',
  [ProjectStatus.LOST]: 'thumb_down',
  [ProjectStatus.WON]: 'emoji_events'
};

export interface KanbanBoardDto {
  inactivityDays: number;
  columns: { [status: string]: KanbanProjectCardDto[] };
  columnOrder: string[];
}

export interface KanbanProjectCardDto {
  projectId: number;
  role: string;
  status: string;
  clientName?: string;
  dailyRate: number;
  workMode?: string;
  techStack?: string;
  sourceName?: string;
  startDate?: string;
  durationInMonths?: number;
  notes?: string;
  personalRating?: number;
  isFavorite?: boolean;
  boardPosition?: number;
  updatedAt?: string;
  refusedBy?: RefusedBy;
  lostReason?: LostReason;
  isPotentialDuplicate?: boolean;
  totalSteps: number;
  completedSteps: number;
  failedSteps: number;
}

export type WorkMode = 'REMOTE' | 'ONSITE' | 'HYBRID';

/** Who turned a refused opportunity down: the freelance, or the recruiter (the client or the ESN). */
export type RefusedBy = 'FREELANCE' | 'RECRUITER';

export const REFUSED_BY: RefusedBy[] = ['RECRUITER', 'FREELANCE'];

/** Who refused an opportunity, and why when it was said. */
export interface Refusal {
  refusedBy: RefusedBy;
  lostReason?: LostReason;
}

export type LostReason =
  | 'RATE_TOO_LOW'
  | 'POSITION_FILLED'
  | 'NO_RESPONSE'
  | 'PROFILE_MISMATCH'
  | 'CLIENT_CANCELED'
  | 'ACCEPTED_OTHER_OFFER'
  | 'OTHER';

/** Working days a year assumed for a mission that states none, as the server's yearly review does. */
export const DEFAULT_DAYS_PER_YEAR = 218;

export const LOST_REASONS: LostReason[] = [
  'RATE_TOO_LOW',
  'POSITION_FILLED',
  'NO_RESPONSE',
  'PROFILE_MISMATCH',
  'CLIENT_CANCELED',
  'ACCEPTED_OTHER_OFFER',
  'OTHER'
];

export interface ProjectDto {
  id?: number;
  role: string;
  status?: ProjectStatus;
  description?: string;
  techStack?: string;
  dailyRate: number;
  clientDailyRate?: number;
  askedDailyRate?: number;
  offeredDailyRate?: number;
  workMode?: WorkMode;
  remoteDaysPerMonth?: number;
  onsiteDaysPerMonth?: number;
  advantages?: string;
  startDate?: string;
  durationInMonths?: number;
  orderRenewalInMonths?: number;
  daysPerYear?: number;
  documents?: string[];
  link?: string;
  personalRating?: number;
  notes?: string;
  /** When the opportunity was archived; absent while it is in the pipeline. */
  archivedAt?: string;
  /** The day a won mission ended, and why; absent while it runs. */
  endedOn?: string;
  endReason?: string;
  isFavorite?: boolean;
  refusedBy?: RefusedBy;
  lostReason?: LostReason;
  freelanceId?: number;
  clientId?: number;
  clientName?: string;
  middlemanId?: number;
  middlemanName?: string;
  sourceId?: number;
  sourceName?: string;
  seasonId?: number;
  seasonName?: string;
  totalRevenue?: number;
  margin?: number;
  marginPercentage?: number;
  totalSteps?: number;
  completedSteps?: number;
  failedSteps?: number;
}

export interface DashboardStatsDto {
  targetDailyRate?: number | null;
  averageObtainedDailyRate?: number | null;
  totalProjects: number;
  averageDailyRate: number;
  totalEstimatedRevenue: number;
  forecastRevenue: number;
  activeProjects: number;
  wonProjects: number;
  lostProjects: number;
  totalBenchDays: number;
  benchPeriods: number;
  estimatedBenchCost: number;
  projectsByStatus: { [status: string]: number };
  projectsByWorkMode: { [mode: string]: number };
  lostReasonsBreakdown: { [reason: string]: number };
  dailyRateRanges: DailyRateRange[];
  sourceRoi: SourceRoi[];
  dailyRateEvolution: DailyRateEvolution[];
  conversionFunnel: ConversionFunnelStage[];
  funnelBySource: FunnelBreakdown[];
  funnelByClientType: FunnelBreakdown[];
  funnelByEsn: FunnelBreakdown[];
  missionsEndingSoon: MissionEndingSoon[];
  staleOpportunities: StaleOpportunity[];
  upcomingRenewals: UpcomingRenewal[];
  onThisDay: OnThisDayItem[];
  dormantContacts: DormantContact[];
  /** Contacts whose latest exchange is a message sent and unanswered for the follow-up period, longest waiting first. */
  followUpsDue?: FollowUpDue[];
  /** Issued invoices past their due date, the oldest due first. */
  overdueInvoices?: OverdueInvoice[];
  /** The last 90 days against the 90 before: opportunities, interviews, missions won and their daily rate. */
  trends?: Trend[];
  /** Compliance documents expired or expiring within 30 days, the soonest first. */
  expiringDocuments?: ExpiringDocument[];
  /** Running purchase orders with 80 % of a ceiling used or more, the most used first. */
  purchaseOrderAlerts?: PurchaseOrderAlert[];
  /** Interview steps planned in the next seven days, soonest first. */
  upcomingInterviews?: UpcomingInterview[];
  skillTrends: SkillTrend[];
  processDurations: ProcessDuration[];
  activityHeatmap: ActivityDay[];
}

export interface DailyRateRange {
  label: string;
  count: number;
}

export interface SourceRoi {
  sourceName: string;
  totalProjects: number;
  wonProjects: number;
  conversionRate: number;
}

export interface DailyRateEvolution {
  period: string;
  averageAskedRate: number;
  averageObtainedRate: number;
  projectCount: number;
}

export interface ConversionFunnelStage {
  stage: string;
  count: number;
  conversionRate: number;
}

export interface FunnelBreakdown {
  group: string;
  stages: ConversionFunnelStage[];
}

export interface MissionEndingSoon {
  projectId: number;
  role: string;
  clientName: string;
  endDate: string;
  daysUntilEnd: number;
}

export interface UpcomingInterview {
  stepId: number;
  projectId: number;
  title: string;
  role: string;
  clientName?: string;
  date: string;
}

export interface StaleOpportunity {
  projectId: number;
  role: string;
  clientName: string;
  status: string;
  daysSinceActivity: number;
}

export interface UpcomingRenewal {
  projectId: number;
  role: string;
  clientName: string;
  renewalDate: string;
  daysUntilRenewal: number;
}

export interface OnThisDayItem {
  type: 'PROJECT' | 'CONTACT';
  id: number;
  label: string;
  subLabel: string;
  date: string;
  yearsAgo: number;
}

export type TrendMetric = 'OPPORTUNITIES' | 'INTERVIEWS' | 'WON' | 'OBTAINED_RATE';

/** One metric over the last 90 days and the 90 before, with the change in percent when there is something to compare. */
export interface Trend {
  metric: TrendMetric;
  current: number | null;
  previous: number | null;
  change: number | null;
}

export interface OverdueInvoice {
  invoiceId: number;
  number: string;
  clientName: string;
  total: number;
  dueDate: string;
  daysLate: number;
}

export interface FollowUpDue {
  contactId: number;
  name: string;
  clientName?: string;
  channel: string;
  sentOn: string;
  daysWaiting: number;
}

export interface DormantContact {
  id: number;
  name: string;
  clientName: string;
  monthsSinceActivity: number;
}

export interface SkillTrend {
  skill: string;
  count: number;
  averageDailyRate: number;
}

export interface ProcessDuration {
  group: string;
  averageDays: number;
  count: number;
}

export interface ActivityDay {
  date: string;
  count: number;
}

export interface ProjectNote {
  id: number;
  projectId: number;
  content: string;
  createdAt: string;
}

export interface CreateProjectDto {
  role: string;
  status?: ProjectStatus;
  description?: string;
  techStack?: string;
  dailyRate: number;
  clientDailyRate?: number;
  askedDailyRate?: number;
  offeredDailyRate?: number;
  workMode?: WorkMode;
  remoteDaysPerMonth?: number;
  onsiteDaysPerMonth?: number;
  advantages?: string;
  startDate?: string;
  durationInMonths?: number;
  orderRenewalInMonths?: number;
  daysPerYear?: number;
  documents?: string[];
  link?: string;
  personalRating?: number;
  notes?: string;
  isFavorite?: boolean;
  freelanceId: number;
  clientId?: number;
  middlemanId?: number;
  sourceId?: number;
  seasonId?: number;
}

/** One change applied to several opportunities at once, from the Kanban board's or the list's selection. */
export interface ProjectBulkUpdate {
  projectIds: number[];
  action: 'STATUS' | 'SOURCE' | 'TAG' | 'ARCHIVE' | 'DELETE';
  status?: ProjectStatus;
  /** Who refused them, and why when it was said, when moved to LOST. */
  refusedBy?: RefusedBy;
  lostReason?: LostReason;
  sourceId?: number;
  /** The tag added to their tech stack. */
  tag?: string;
}

/** A file kept with an opportunity; {@code inline} when the browser may show it in the page (PDF and images). */
export interface ProjectAttachment {
  id: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
  inline: boolean;
}
