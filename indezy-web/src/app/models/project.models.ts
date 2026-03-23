// Project related interfaces and types

export enum ProjectStatus {
  IDENTIFIED = 'IDENTIFIED',
  APPLIED = 'APPLIED',
  INTERVIEW = 'INTERVIEW',
  OFFER = 'OFFER',
  WON = 'WON',
  LOST = 'LOST'
}

export const PROJECT_STATUS_LABELS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.IDENTIFIED]: 'Identifié',
  [ProjectStatus.APPLIED]: 'Postulé',
  [ProjectStatus.INTERVIEW]: 'Entretien',
  [ProjectStatus.OFFER]: 'Offre',
  [ProjectStatus.WON]: 'Gagné',
  [ProjectStatus.LOST]: 'Perdu'
};

export const PROJECT_STATUS_COLORS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.IDENTIFIED]: '#78909c',
  [ProjectStatus.APPLIED]: '#42a5f5',
  [ProjectStatus.INTERVIEW]: '#ffa726',
  [ProjectStatus.OFFER]: '#ab47bc',
  [ProjectStatus.WON]: '#66bb6a',
  [ProjectStatus.LOST]: '#ef5350'
};

export const PROJECT_STATUS_ICONS: { [key in ProjectStatus]: string } = {
  [ProjectStatus.IDENTIFIED]: 'search',
  [ProjectStatus.APPLIED]: 'send',
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
  totalSteps: number;
  completedSteps: number;
  failedSteps: number;
}

export type WorkMode = 'REMOTE' | 'ONSITE' | 'HYBRID';

export interface ProjectDto {
  id?: number;
  role: string;
  status?: ProjectStatus;
  description?: string;
  techStack?: string;
  dailyRate: number;
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
  freelanceId?: number;
  clientId?: number;
  clientName?: string;
  middlemanId?: number;
  middlemanName?: string;
  sourceId?: number;
  sourceName?: string;
  totalRevenue?: number;
  totalSteps?: number;
  completedSteps?: number;
  failedSteps?: number;
}

export interface DashboardStatsDto {
  totalProjects: number;
  averageDailyRate: number;
  totalEstimatedRevenue: number;
  activeProjects: number;
  wonProjects: number;
  lostProjects: number;
  projectsByStatus: { [status: string]: number };
  projectsByWorkMode: { [mode: string]: number };
  dailyRateRanges: DailyRateRange[];
}

export interface DailyRateRange {
  label: string;
  count: number;
}

export interface CreateProjectDto {
  role: string;
  status?: ProjectStatus;
  description?: string;
  techStack?: string;
  dailyRate: number;
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
  freelanceId: number;
  clientId?: number;
  middlemanId?: number;
  sourceId?: number;
}

export interface UpdateProjectDto {
  id: number;
  role?: string;
  status?: ProjectStatus;
  description?: string;
  techStack?: string;
  dailyRate?: number;
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
}
