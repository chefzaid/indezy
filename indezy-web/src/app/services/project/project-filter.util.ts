import { ProjectDto } from '../../models';
import { CommuteInfoDto } from '../../models/commute.models';

export type RateFilterValue = number | string | null;

export type DateFilterValue = string | Date | null;

export interface ProjectFilterValues {
  searchQuery?: string;
  minRate?: RateFilterValue;
  maxRate?: RateFilterValue;
  workMode?: string;
  techStack?: string;
  status?: string;
  /** '' for every season, 'none' for opportunities outside any season, or a season id. */
  season?: string | number;
  startDateFrom?: DateFilterValue;
  startDateTo?: DateFilterValue;
  endDateFrom?: DateFilterValue;
  endDateTo?: DateFilterValue;
  duration?: string;
  client?: string;
  selectedTechStack?: string[];
  sortBy?: string;
  sortOrder?: string;
}

export type ProjectStatus = 'upcoming' | 'inProgress' | 'completed' | 'unknown';

const DURATION_RANGES: Record<string, [number, number]> = {
  '1-3': [1, 3],
  '3-6': [3, 6],
  '6-12': [6, 12],
  '12+': [12, Infinity]
};

export function filterProjects(projects: ProjectDto[], filters: ProjectFilterValues): ProjectDto[] {
  return projects.filter(project =>
    matchesSearchQuery(project, filters) &&
    matchesRate(project, filters) &&
    matchesWorkMode(project, filters) &&
    matchesStatus(project, filters) &&
    matchesSeason(project, filters) &&
    matchesTechStack(project, filters) &&
    matchesSelectedTechStack(project, filters) &&
    matchesDateRange(project, filters) &&
    matchesDuration(project, filters) &&
    matchesClient(project, filters)
  );
}

export function sortProjects(
  projects: ProjectDto[],
  sortBy: string,
  sortOrder: string,
  commuteData?: Map<number, CommuteInfoDto>
): ProjectDto[] {
  if (sortBy === 'commuteTime') {
    return sortByCommuteTime(projects, sortOrder, commuteData ?? new Map());
  }

  return projects.sort((a, b) => {
    const aValue = sortValue(a, sortBy);
    const bValue = sortValue(b, sortBy);
    if (aValue === null || bValue === null) {
      return 0;
    }
    return compare(aValue, bValue, sortOrder);
  });
}

/** Derives the lifecycle status of a project from its start date and duration. */
export function getProjectStatus(project: ProjectDto, now: Date = new Date()): ProjectStatus {
  if (!project.startDate) {
    return 'unknown';
  }
  const startDate = new Date(project.startDate);
  if (startDate > now) {
    return 'upcoming';
  }
  if (!project.durationInMonths) {
    return 'unknown';
  }
  const endDate = new Date(startDate);
  endDate.setMonth(endDate.getMonth() + project.durationInMonths);
  return endDate > now ? 'inProgress' : 'completed';
}

export function countActiveFilters(filters: ProjectFilterValues): number {
  const active: unknown[] = [
    filters.searchQuery?.trim(),
    filters.minRate,
    filters.maxRate,
    filters.workMode,
    filters.status,
    filters.techStack?.trim(),
    filters.selectedTechStack?.length ? filters.selectedTechStack : undefined,
    filters.startDateFrom,
    filters.startDateTo,
    filters.endDateFrom,
    filters.endDateTo,
    filters.duration,
    filters.client?.trim()
  ];
  return active.filter(Boolean).length;
}

function matchesSearchQuery(project: ProjectDto, filters: ProjectFilterValues): boolean {
  if (!filters.searchQuery?.trim()) {
    return true;
  }
  const query = filters.searchQuery.toLowerCase();
  const searchableText = [
    project.role, project.clientName, project.description, project.techStack
  ].filter(Boolean).join(' ').toLowerCase();
  return searchableText.includes(query);
}

function matchesRate(project: ProjectDto, filters: ProjectFilterValues): boolean {
  const min = filters.minRate ? Number(filters.minRate) : null;
  const max = filters.maxRate ? Number(filters.maxRate) : null;
  if (min !== null && project.dailyRate && project.dailyRate < min) {
    return false;
  }
  if (max !== null && project.dailyRate && project.dailyRate > max) {
    return false;
  }
  return true;
}

function matchesWorkMode(project: ProjectDto, filters: ProjectFilterValues): boolean {
  return !filters.workMode || project.workMode === filters.workMode;
}

function matchesStatus(project: ProjectDto, filters: ProjectFilterValues): boolean {
  return !filters.status || project.status === filters.status;
}

function matchesSeason(project: ProjectDto, filters: ProjectFilterValues): boolean {
  if (filters.season === undefined || filters.season === null || filters.season === '') {
    return true;
  }
  if (filters.season === 'none') {
    return project.seasonId === undefined || project.seasonId === null;
  }
  return project.seasonId === Number(filters.season);
}

function matchesTechStack(project: ProjectDto, filters: ProjectFilterValues): boolean {
  if (filters.techStack && project.techStack &&
      !project.techStack.toLowerCase().includes(filters.techStack.toLowerCase())) {
    return false;
  }
  return true;
}

function matchesSelectedTechStack(project: ProjectDto, filters: ProjectFilterValues): boolean {
  if (!filters.selectedTechStack || filters.selectedTechStack.length === 0) {
    return true;
  }
  const projectTechStack = project.techStack?.toLowerCase() ?? '';
  return filters.selectedTechStack.some(tech => projectTechStack.includes(tech.toLowerCase()));
}

function matchesDateRange(project: ProjectDto, filters: ProjectFilterValues): boolean {
  if (filters.startDateFrom && project.startDate &&
      new Date(project.startDate) < new Date(filters.startDateFrom)) {
    return false;
  }
  if (filters.startDateTo && project.startDate &&
      new Date(project.startDate) > new Date(filters.startDateTo)) {
    return false;
  }
  if (filters.endDateFrom || filters.endDateTo) {
    const endDate = getProjectEndDate(project);
    if (!endDate) {
      return false;
    }
    if (filters.endDateFrom && endDate < new Date(filters.endDateFrom)) {
      return false;
    }
    if (filters.endDateTo && endDate > new Date(filters.endDateTo)) {
      return false;
    }
  }
  return true;
}

/** Mission end date derived from its start date and duration, or null when either is unknown. */
export function getProjectEndDate(project: ProjectDto): Date | null {
  if (!project.startDate || !project.durationInMonths) {
    return null;
  }
  const endDate = new Date(project.startDate);
  endDate.setMonth(endDate.getMonth() + project.durationInMonths);
  return endDate;
}

function matchesDuration(project: ProjectDto, filters: ProjectFilterValues): boolean {
  if (!filters.duration || !project.durationInMonths) {
    return true;
  }
  const range = DURATION_RANGES[filters.duration];
  if (!range) {
    return true;
  }
  return project.durationInMonths >= range[0] && project.durationInMonths <= range[1];
}

function matchesClient(project: ProjectDto, filters: ProjectFilterValues): boolean {
  if (!filters.client?.trim()) {
    return true;
  }
  return project.clientName?.toLowerCase().includes(filters.client.toLowerCase()) ?? false;
}

function sortValue(project: ProjectDto, sortBy: string): string | number | null {
  switch (sortBy) {
    case 'startDate':
      return project.startDate ? new Date(project.startDate).getTime() : 0;
    case 'dailyRate':
      return project.dailyRate || 0;
    case 'role':
      return project.role?.toLowerCase() || '';
    case 'clientName':
      return project.clientName?.toLowerCase() || '';
    default:
      return null;
  }
}

function compare(a: string | number, b: string | number, sortOrder: string): number {
  if (a < b) {
    return sortOrder === 'asc' ? -1 : 1;
  }
  if (a > b) {
    return sortOrder === 'asc' ? 1 : -1;
  }
  return 0;
}

function sortByCommuteTime(
  projects: ProjectDto[],
  sortOrder: string,
  commuteData: Map<number, CommuteInfoDto>
): ProjectDto[] {
  return projects.sort((a, b) => {
    const durationA = (a.id ? commuteData.get(a.id)?.durationInSeconds : undefined) ?? Number.MAX_SAFE_INTEGER;
    const durationB = (b.id ? commuteData.get(b.id)?.durationInSeconds : undefined) ?? Number.MAX_SAFE_INTEGER;
    return compare(durationA, durationB, sortOrder);
  });
}
