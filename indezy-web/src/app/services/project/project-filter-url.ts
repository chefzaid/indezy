import { ParamMap, Params } from '@angular/router';
import { DateFilterValue, ProjectFilterValues } from './project-filter.util';

/**
 * The projects list's filters in its address, so a filtered view can be bookmarked or shared: one short parameter per
 * filter set, nothing for a filter left at its default.
 */
const TEXT_PARAMS: [keyof ProjectFilterValues, string][] = [
  ['searchQuery', 'q'], ['workMode', 'mode'], ['techStack', 'tech'], ['status', 'status'], ['duration', 'duration'],
  ['client', 'client'], ['sortBy', 'sort'], ['sortOrder', 'order']
];
const NUMBER_PARAMS: [keyof ProjectFilterValues, string][] = [['minRate', 'min'], ['maxRate', 'max']];
const DATE_PARAMS: [keyof ProjectFilterValues, string][] = [
  ['startDateFrom', 'from'], ['startDateTo', 'to'], ['endDateFrom', 'endFrom'], ['endDateTo', 'endTo']
];
const DEFAULTS: Partial<Record<keyof ProjectFilterValues, string>> = { sortBy: 'startDate', sortOrder: 'desc' };
const ARCHIVE_VALUES = ['active', 'archived'];
const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

/** A filter's day as yyyy-MM-dd, on the local calendar. */
function day(value: DateFilterValue | undefined): string | null {
  if (!value) {
    return null;
  }
  if (typeof value === 'string') {
    return DAY.test(value) ? value : null;
  }
  const pad = (n: number): string => String(n).padStart(2, '0');
  return Number.isNaN(value.getTime()) ? null : `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}`;
}

/** The address parameters of {@code filters}. */
export function filtersToParams(filters: ProjectFilterValues): Params {
  const params: Params = {};
  for (const [key, param] of TEXT_PARAMS) {
    const value = String(filters[key] ?? '').trim();
    if (value && value !== DEFAULTS[key]) {
      params[param] = value;
    }
  }
  for (const [key, param] of NUMBER_PARAMS) {
    const value = filters[key];
    if (value !== null && value !== undefined && value !== '') {
      params[param] = String(value);
    }
  }
  for (const [key, param] of DATE_PARAMS) {
    const value = day(filters[key] as DateFilterValue);
    if (value) {
      params[param] = value;
    }
  }
  if (filters.selectedTechStack?.length) {
    params['tags'] = filters.selectedTechStack.join(',');
  }
  if (filters.season !== undefined && filters.season !== null && filters.season !== '') {
    params['season'] = String(filters.season);
  }
  if (filters.archived) {
    params['archived'] = filters.archived;
  }
  return params;
}

/** The filters an address sets, leaving out what it cannot mean. */
export function filtersFromParams(params: ParamMap): Partial<ProjectFilterValues> {
  const filters: Record<string, unknown> = {};
  for (const [key, param] of TEXT_PARAMS) {
    const value = params.get(param)?.trim();
    if (value) {
      filters[key] = value;
    }
  }
  for (const [key, param] of NUMBER_PARAMS) {
    const value = Number(params.get(param));
    if (params.get(param) && Number.isFinite(value) && value >= 0) {
      filters[key] = value;
    }
  }
  for (const [key, param] of DATE_PARAMS) {
    const match = DAY.exec(params.get(param) ?? '');
    if (match) {
      filters[key] = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
    }
  }
  const tags = (params.get('tags') ?? '').split(',').map(tag => tag.trim()).filter(Boolean);
  if (tags.length) {
    filters['selectedTechStack'] = tags;
  }
  const season = params.get('season');
  if (season === 'none' || /^[1-9]\d*$/.test(season ?? '')) {
    filters['season'] = season === 'none' ? season : Number(season);
  }
  const archived = params.get('archived');
  if (archived && ARCHIVE_VALUES.includes(archived)) {
    filters['archived'] = archived;
  }
  return filters as Partial<ProjectFilterValues>;
}
