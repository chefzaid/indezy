// Project related interfaces and types

export interface ProjectDto {
  id?: number;
  role: string;
  description?: string;
  techStack?: string;
  dailyRate: number;
  workMode?: 'REMOTE' | 'ONSITE' | 'HYBRID';
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

export interface CreateProjectDto {
  role: string;
  description?: string;
  techStack?: string;
  dailyRate: number;
  workMode?: 'REMOTE' | 'ONSITE' | 'HYBRID';
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
  description?: string;
  techStack?: string;
  dailyRate?: number;
  workMode?: 'REMOTE' | 'ONSITE' | 'HYBRID';
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
