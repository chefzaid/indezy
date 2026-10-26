import { convertToParamMap } from '@angular/router';
import { filtersFromParams, filtersToParams } from './project-filter-url';

describe('project filters in the address', () => {
  it('writes only the filters that are set, with short names', () => {
    expect(filtersToParams({
      searchQuery: ' java ', minRate: 500, maxRate: '', workMode: 'REMOTE', status: 'OFFER', season: 3, archived: 'active',
      startDateFrom: new Date(2026, 0, 5), startDateTo: '2026-03-01', endDateTo: 'not a day', selectedTechStack: ['Java', 'Kafka'],
      sortBy: 'startDate', sortOrder: 'asc', client: '', duration: ''
    })).toEqual({
      q: 'java', min: '500', mode: 'REMOTE', status: 'OFFER', season: '3', archived: 'active', from: '2026-01-05',
      to: '2026-03-01', tags: 'Java,Kafka', order: 'asc'
    });
    expect(filtersToParams({ sortBy: 'startDate', sortOrder: 'desc', selectedTechStack: [], season: '', endDateFrom: null })).toEqual({});
    expect(filtersToParams({ startDateFrom: new Date(Number.NaN), season: 'none' })).toEqual({ season: 'none' });
  });

  it('reads them back, leaving out what they cannot mean', () => {
    const filters = filtersFromParams(convertToParamMap({
      q: 'java', min: '500', max: '-3', mode: 'REMOTE', from: '2026-01-05', to: '5 janvier', tags: 'Java, ,Kafka', season: '3',
      archived: 'archived', sort: 'dailyRate'
    }));

    expect(filters).toEqual({
      searchQuery: 'java', minRate: 500, workMode: 'REMOTE', startDateFrom: new Date(2026, 0, 5), selectedTechStack: ['Java', 'Kafka'],
      season: 3, archived: 'archived', sortBy: 'dailyRate'
    });
    expect(filtersFromParams(convertToParamMap({ season: 'none', archived: 'everything' }))).toEqual({ season: 'none' });
    expect(filtersFromParams(convertToParamMap({ season: '0' }))).toEqual({});
  });

  it('gives back the filters it wrote', () => {
    const written = { searchQuery: 'kafka', minRate: 600, startDateTo: new Date(2026, 5, 30), selectedTechStack: ['Go'], archived: 'active' as const };

    expect(filtersFromParams(convertToParamMap(filtersToParams(written)))).toEqual(written);
  });
});
