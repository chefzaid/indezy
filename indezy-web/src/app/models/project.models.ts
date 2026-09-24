// Project related interfaces and types

export enum ProjectStatus {
  CONTACT = 'CONTACT',
  INTERVIEW = 'INTERVIEW',
  OFFER = 'OFFER',
  WON = 'WON',
  LOST = 'LOST'
}

/** Pipeline stage colors, dark enough to carry white text in status pills. */
export const PROJECT_STATUS_COLORS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.CONTACT]: '#1e88e5',
  [ProjectStatus.INTERVIEW]: '#ef6c00',
  [ProjectStatus.OFFER]: '#8e24aa',
  [ProjectStatus.WON]: '#2e7d32',
  [ProjectStatus.LOST]: '#d32f2f'
};

export const PROJECT_STATUS_ICONS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.CONTACT]: 'contact_mail',
  [ProjectStatus.INTERVIEW]: 'groups',
  [ProjectStatus.OFFER]: 'handshake',
  [ProjectStatus.WON]: 'emoji_events',
  [ProjectStatus.LOST]: 'thumb_down'
};

export interface KanbanBoardDto {
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
  lostReason?: LostReason;
  isPotentialDuplicate?: boolean;
  totalSteps: number;
  completedSteps: number;
  failedSteps: number;
}

export type WorkMode = 'REMOTE' | 'ONSITE' | 'HYBRID';

export type LostReason =
  | 'RATE_TOO_LOW'
  | 'POSITION_FILLED'
  | 'NO_RESPONSE'
  | 'PROFILE_MISMATCH'
  | 'CLIENT_CANCELED'
  | 'ACCEPTED_OTHER_OFFER'
  | 'OTHER';

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
  isFavorite?: boolean;
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

export interface UpdateProjectDto {
  id: number;
  role?: string;
  status?: ProjectStatus;
  description?: string;
  techStack?: string;
  dailyRate?: number;
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
  clientId?: number;
  middlemanId?: number;
  sourceId?: number;
  seasonId?: number;
}
