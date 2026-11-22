import { PurchaseOrderAlert } from './purchase-order.models';
import { ExpiringDocument } from './compliance.models';
// Project related interfaces and types

/**
 * The pipeline stages in the order of the Kanban columns: the refused opportunities' columns come before the accepted
 * one's. A refused opportunity is abandoned by the recruiter (LOST) or withdrawn by the freelance (WITHDRAWN).
 */
export enum ProjectStatus {
  CONTACT = 'CONTACT',
  INTERVIEW = 'INTERVIEW',
  OFFER = 'OFFER',
  LOST = 'LOST',
  WITHDRAWN = 'WITHDRAWN',
  WON = 'WON'
}

/** Whether a status says the opportunity was refused, by the recruiter or by the freelance. */
export function isRefusedStatus(status?: ProjectStatus | string | null): boolean {
  return status === ProjectStatus.LOST || status === ProjectStatus.WITHDRAWN;
}

/** Whether an opportunity's pipeline is over: accepted or refused. */
export function isClosedStatus(status?: ProjectStatus | string | null): boolean {
  return status === ProjectStatus.WON || isRefusedStatus(status);
}

/**
 * Pipeline stage colors, dark enough to carry white text in status pills (4.5:1 contrast at least). A lost
 * opportunity is a muted rose, a withdrawn one a slate, not an alarm red: losing one is part of the pipeline, not an error.
 */
export const PROJECT_STATUS_COLORS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.CONTACT]: '#2563eb',
  [ProjectStatus.INTERVIEW]: '#b45309',
  [ProjectStatus.OFFER]: '#7c3aed',
  [ProjectStatus.LOST]: '#8f5a68',
  [ProjectStatus.WITHDRAWN]: '#5b6475',
  [ProjectStatus.WON]: '#15803d'
};

export const PROJECT_STATUS_ICONS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.CONTACT]: 'contact_mail',
  [ProjectStatus.INTERVIEW]: 'groups',
  [ProjectStatus.OFFER]: 'handshake',
  [ProjectStatus.LOST]: 'thumb_down',
  [ProjectStatus.WITHDRAWN]: 'back_hand',
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
  /** Its place of work, else its client's city. */
  place?: string | null;
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

/** Who turned a refused opportunity down, which its status tells: the freelance, or the recruiter (the client or the ESN). */
export type RefusedBy = 'FREELANCE' | 'RECRUITER';

/** Why an opportunity was refused, when it was said; who refused it is its status. */
export interface Refusal {
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
  /** Where the mission is worked, when not at the client's address; the commute goes there. */
  workAddress?: string | null;
  /** Where the mission is worked, as shown: its place of work, else its client's city; set by the server. */
  place?: string | null;
  /** Months the ESN's non-solicitation clause runs after the mission. */
  nonSolicitationMonths?: number | null;
  /** The contract's points checked before signing. */
  contractChecks?: ContractCheck[];
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

/** This year's days of validated activity reports, the profile's yearly target and the year at the completed months' pace. */
export interface BilledDays {
  year: number;
  billed: number;
  target: number | null;
  projected: number | null;
}

export interface DashboardStatsDto {
  targetDailyRate?: number | null;
  billedDays?: BilledDays | null;
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
  /** This week's goal against the bench; null while no mission ends within 12 weeks. */
  benchPlan?: BenchPlan | null;
  staleOpportunities: StaleOpportunity[];
  upcomingRenewals: UpcomingRenewal[];
  onThisDay: OnThisDayItem[];
  dormantContacts: DormantContact[];
  /** Contacts whose latest exchange is a message sent and unanswered for the follow-up period, longest waiting first. */
  followUpsDue?: FollowUpDue[];
  /** Stages to update, read from what contacts recently said about open opportunities. */
  statusSuggestions?: StatusSuggestion[];
  /** Issued invoices past their due date, the oldest due first. */
  overdueInvoices?: OverdueInvoice[];
  /** The last 90 days against the 90 before: opportunities, interviews, missions won and their daily rate. */
  trends?: Trend[];
  /** Compliance documents expired or expiring within 30 days, the soonest first. */
  expiringDocuments?: ExpiringDocument[];
  /** Trainings whose funding is still to ask with its deadline within 14 days or past, the soonest first. */
  trainingFundingDue?: TrainingFundingDue[];
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

/** This week's goal against the bench: the mission ending (none between missions), the pipeline and the goal. */
export interface BenchPlan {
  projectId: number | null;
  role: string | null;
  endDate: string | null;
  weeksLeft: number;
  activeOpportunities: number;
  advancedOpportunities: number;
  addedThisWeek: number;
  daysSinceLastAdded: number | null;
  weeklyGoal: number;
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
  /** The current period's daily rate. */
  dailyRate?: number | null;
  /** That rate against the market median of the mission's first skill. */
  market?: { percent: number; median: number; offers: number; skill: string; regionName?: string } | null;
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

/** A stage to update, from an exchange in which a contact turned an opportunity down or asked for an interview. */
export interface StatusSuggestion {
  projectId: number;
  interactionId: number;
  role: string | null;
  clientName?: string | null;
  status: ProjectStatus;
  suggested: ProjectStatus;
  excerpt: string;
  occurredOn: string;
}

export interface FollowUpDue {
  contactId: number;
  name: string;
  clientName?: string;
  channel: string;
  sentOn: string;
  daysWaiting: number;
  /** How many days the contact's company usually takes to answer; absent until two answers are recorded. */
  usualAnswerDays?: number | null;
  /** The opportunity the waiting message was about, to draft the follow-up from. */
  projectId?: number | null;
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
  /** Opportunities created and interview steps held that day. */
  opportunities?: number;
  interviews?: number;
  /** The first few of the day's activity, opportunities first. */
  items?: ActivityItem[];
}

/** An opportunity created or an interview step held on a day of the activity heatmap. */
export interface ActivityItem {
  kind: 'OPPORTUNITY' | 'INTERVIEW';
  /** The opportunity's role, or the step's title. */
  label: string | null;
  client: string | null;
  projectId: number | null;
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
  /** Months the ESN's non-solicitation clause runs after the mission. */
  nonSolicitationMonths?: number | null;
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
  /** Why they were refused, when it was said, when moved to LOST or WITHDRAWN. */
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

/** An opportunity for the end client of a mission won through an ESN, while that ESN's non-solicitation clause runs. */
export interface NonSolicitation {
  projectId: number;
  missionId: number;
  missionRole: string;
  middlemanName: string;
  clientName: string;
  months: number;
  /** Absent while the mission runs open-ended. */
  endsOn?: string | null;
}

/** The points to check in a mission's contract, in the order they are shown. */
export const CONTRACT_POINTS = ['PAYMENT_TERMS', 'NOTICE_PERIOD', 'NON_SOLICITATION', 'INTELLECTUAL_PROPERTY', 'LIABILITY_INSURANCE',
  'RATE_REVISION', 'PLACE_OF_WORK'] as const;
export type ContractPoint = typeof CONTRACT_POINTS[number];

/** One point of a mission's contract checklist. */
export interface ContractCheck {
  point: ContractPoint;
  checked: boolean;
  note?: string | null;
}

/** A training's funding request to send: its title and the fund's deadline. */
export interface TrainingFundingDue {
  trainingId: number;
  title: string;
  deadline: string;
}

/** Who pays a co-optation bonus: the freelancer to who referred the mission, or someone to the freelancer. */
export type ReferralDirection = 'I_PAY' | 'I_RECEIVE';
/** How a co-optation bonus is counted: fixed, per day worked, or a share of what the mission earns. */
export type ReferralBonusKind = 'FIXED' | 'PER_DAY' | 'PERCENT';
/** Where a co-optation bonus stands. */
export type ReferralStatus = 'PROMISED' | 'DUE' | 'PAID';

/** A mission's co-optation as saved. */
export interface ProjectReferral {
  referrer: string;
  direction: ReferralDirection;
  bonusKind: ReferralBonusKind | null;
  bonusAmount: number | null;
  status: ReferralStatus;
  paidOn: string | null;
  note: string | null;
}

/** A mission's co-optation as read: with the days worked, what the mission earned, and the bonus that comes of it. */
export interface ProjectReferralView {
  projectId: number;
  role: string;
  clientName: string | null;
  referral: ProjectReferral;
  workedDays: number;
  earned: number;
  bonus: number | null;
}
