import { ProjectDto, ProjectStatus } from '../../models';
import { countActiveFilters, filterProjects, getProjectEndDate } from './project-filter.util';

const project = (overrides: Partial<ProjectDto>): ProjectDto => ({ role: 'Dev', dailyRate: 600, ...overrides });

describe('project filters', () => {
  const projects = [
    project({ id: 1, status: ProjectStatus.WON, startDate: '2026-01-01', durationInMonths: 6 }),
    project({ id: 2, status: ProjectStatus.LOST, startDate: '2026-03-01', durationInMonths: 12 }),
    project({ id: 3, status: ProjectStatus.WON })
  ];

  it('filters by pipeline status', () => {
    expect(filterProjects(projects, { status: 'WON' }).map(p => p.id)).toEqual([1, 3]);
  });

  it('filters by derived end date and drops projects without one', () => {
    const result = filterProjects(projects, { endDateFrom: '2026-08-01' });
    expect(result.map(p => p.id)).toEqual([2]);
  });

  it('derives the end date from start date and duration', () => {
    expect(getProjectEndDate(projects[0])?.getMonth()).toBe(6);
    expect(getProjectEndDate(projects[2])).toBeNull();
  });

  it('counts only filters that are applied', () => {
    expect(countActiveFilters({ status: 'WON', searchQuery: ' ', client: 'Acme' })).toBe(2);
  });
});
