import { ProjectDto, ProjectStatus } from '../../models';
import { CommuteInfoDto } from '../../models/commute.models';
import { countActiveFilters, filterProjects, getProjectEndDate, getProjectStatus, sortProjects } from './project-filter.util';

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

  it('filters by season, including opportunities outside any season', () => {
    const seasonal = [
      project({ id: 4, seasonId: 1 }),
      project({ id: 5, seasonId: 2 }),
      project({ id: 6 })
    ];
    expect(filterProjects(seasonal, { season: 1 }).map(p => p.id)).toEqual([4]);
    expect(filterProjects(seasonal, { season: '2' }).map(p => p.id)).toEqual([5]);
    expect(filterProjects(seasonal, { season: 'none' }).map(p => p.id)).toEqual([6]);
    expect(filterProjects(seasonal, { season: '' }).map(p => p.id)).toEqual([4, 5, 6]);
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

  it('counts a chosen season, or opportunities outside any season, but not every season', () => {
    expect(countActiveFilters({ season: '' })).toBe(0);
    expect(countActiveFilters({ season: 'none' })).toBe(1);
    expect(countActiveFilters({ season: 4, status: 'WON' })).toBe(2);
  });

  it('combines search, rate, work mode, skills and client filters', () => {
    const opportunities = [
      project({ id: 1, role: 'Java Developer', clientName: 'Acme', workMode: 'REMOTE', techStack: 'Java, Spring' }),
      project({ id: 2, role: 'Java Developer', clientName: 'Acme', workMode: 'ONSITE', techStack: 'Java' }),
      project({ id: 3, role: 'Designer', clientName: 'Studio', workMode: 'REMOTE', techStack: 'Figma', dailyRate: 450 })
    ];
    expect(filterProjects(opportunities, {
      searchQuery: 'JAVA', minRate: '550', maxRate: 650, workMode: 'REMOTE',
      techStack: 'spring', selectedTechStack: ['Spring'], client: 'acme'
    }).map(p => p.id)).toEqual([1]);
    expect(filterProjects(opportunities, { minRate: 550 }).map(p => p.id)).toEqual([1, 2]);
    expect(filterProjects(opportunities, { maxRate: '500' }).map(p => p.id)).toEqual([3]);
    expect(filterProjects(opportunities, { techStack: 'figma' }).map(p => p.id)).toEqual([3]);
    expect(filterProjects(opportunities, { selectedTechStack: ['java', 'Angular'] }).map(p => p.id)).toEqual([1, 2]);
    expect(filterProjects(opportunities, { client: 'studio' }).map(p => p.id)).toEqual([3]);
    expect(filterProjects([project({})], { selectedTechStack: ['Java'], client: 'Acme' })).toEqual([]);
    expect(filterProjects([project({})], { client: 'Acme' })).toEqual([]);
  });

  it('includes date boundaries and filters by mission duration', () => {
    expect(filterProjects(projects, { startDateFrom: '2026-02-01' }).map(p => p.id)).toEqual([2, 3]);
    expect(filterProjects(projects, { startDateTo: '2026-01-01' }).map(p => p.id)).toEqual([1, 3]);
    expect(filterProjects(projects, { endDateTo: '2026-07-01' }).map(p => p.id)).toEqual([1]);
    expect(filterProjects(projects, { duration: '3-6' }).map(p => p.id)).toEqual([1, 3]);
    expect(filterProjects(projects, { duration: '12+' }).map(p => p.id)).toEqual([2, 3]);
    expect(filterProjects(projects, { duration: 'unrecognized' })).toEqual(projects);
  });

  it('distinguishes upcoming, active, finished and undated missions', () => {
    const now = new Date('2026-04-01');
    expect(getProjectStatus(project({ startDate: '2026-05-01' }), now)).toBe('upcoming');
    expect(getProjectStatus(projects[0], now)).toBe('inProgress');
    expect(getProjectStatus(projects[0], new Date('2027-01-01'))).toBe('completed');
    expect(getProjectStatus(projects[2], now)).toBe('unknown');
    expect(getProjectStatus(project({ startDate: '2026-01-01' }), now)).toBe('unknown');
  });

  it('sorts rates numerically and text without case differences in either direction', () => {
    const opportunities = [
      project({ id: 1, role: 'Zebra', clientName: 'Acme', dailyRate: 600, startDate: '2026-02-01' }),
      project({ id: 2, role: 'alpha', clientName: 'Beta', dailyRate: 1000, startDate: '2026-01-01' }),
      project({ id: 3, role: '', dailyRate: undefined })
    ];
    expect(sortProjects([...opportunities], 'dailyRate', 'asc').map(p => p.id)).toEqual([3, 1, 2]);
    expect(sortProjects([...opportunities], 'dailyRate', 'desc').map(p => p.id)).toEqual([2, 1, 3]);
    expect(sortProjects([...opportunities], 'role', 'asc').map(p => p.id)).toEqual([3, 2, 1]);
    expect(sortProjects([...opportunities], 'clientName', 'desc').map(p => p.id)).toEqual([2, 1, 3]);
    expect(sortProjects([...opportunities], 'startDate', 'asc').map(p => p.id)).toEqual([3, 2, 1]);
    expect(sortProjects([...opportunities], 'unknown', 'asc')).toEqual(opportunities);
    expect(sortProjects(projects.slice(0, 2), 'dailyRate', 'asc')).toEqual(projects.slice(0, 2));
  });

  it('sorts known commute durations before missing estimates', () => {
    const commutes = new Map<number, CommuteInfoDto>([
      [1, { durationInSeconds: 3600 } as CommuteInfoDto],
      [2, { durationInSeconds: 900 } as CommuteInfoDto]
    ]);
    expect(sortProjects([...projects], 'commuteTime', 'asc', commutes).map(p => p.id)).toEqual([2, 1, 3]);
    expect(sortProjects([...projects], 'commuteTime', 'desc', commutes).map(p => p.id)).toEqual([3, 1, 2]);
    expect(sortProjects([...projects], 'commuteTime', 'asc')).toEqual(projects);
    expect(sortProjects([project({}), project({})], 'commuteTime', 'asc')).toHaveSize(2);
  });
});
