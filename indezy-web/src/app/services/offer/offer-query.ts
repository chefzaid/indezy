import { HttpParams } from '@angular/common/http';
import { ParamMap, Params, convertToParamMap } from '@angular/router';
import { WorkMode } from '../../models/project.models';
import {
  OFFER_WORK_MODES,
  OfferFilters,
  OfferQuery,
  OfferSite,
  OfferSort,
  OfferStateUpdate,
  OfferView,
  SENIORITIES,
  Seniority,
  emptyOfferFilters
} from '../../models/offer.models';

export const OFFER_PAGE_SIZES = [20, 50, 100];

/**
 * Whether a state change takes an offer out of the view: unsaved from the saved view, hidden from the feed, shown again
 * from the hidden view.
 */
export function leavesView(view: OfferView, update: OfferStateUpdate): boolean {
  return (view === 'SAVED' && update.saved === false) || (view === 'FEED' && update.hidden === true) ||
    (view === 'HIDDEN' && update.hidden === false);
}
const VIEWS: OfferView[] = ['FEED', 'SAVED', 'HIDDEN', 'APPLIED'];
const SORTS: OfferSort[] = ['NEWEST', 'RATE', 'DISTANCE', 'MATCH'];
const SITES: OfferSite[] = [
  'FREE_WORK',
  'COLLECTIVE',
  'CHERRY_PICK',
  'LEHIBOU',
  'YEETS',
  'WORKDISPO',
  'NEXT_MONDAYS',
  'MINDQUEST',
  'FREELANCE_FACTORY',
  'LITTLE_BIG_CONNECTION',
  'SILKHOM',
  'LEGRATIN',
  'FREELANCE_INFORMATIQUE',
  'ESPACE_FREELANCE',
  'BEYOND_SKILLS_IT',
  'KOINO',
  'SIXIEME_HOMME',
  'FREELANCE_REPUBLIK',
  'EASY_PARTNER',
];
const LIST_KEYS = ['regions', 'departments', 'workModes', 'sites', 'seniorities', 'skills'] as const;
const NUMBER_KEYS = ['rateMin', 'rateMax', 'radiusKm', 'minRemoteDays', 'durationMin', 'durationMax', 'publishedWithinDays'] as const;

export function defaultOfferQuery(): OfferQuery {
  return { ...emptyOfferFilters(), view: 'FEED', sort: 'NEWEST', page: 0, size: OFFER_PAGE_SIZES[0] };
}

/** The feed's query from the page address, ignoring anything unknown or malformed. */
export function offerQueryFromParams(params: ParamMap): OfferQuery {
  const query = defaultOfferQuery();
  const text = params.get('q')?.trim();
  if (text) {
    query.q = text.slice(0, 200);
  }
  for (const key of NUMBER_KEYS) {
    const value = Number(params.get(key));
    if (params.has(key) && Number.isInteger(value) && value >= 0) {
      query[key] = value;
    }
  }
  query.regions = valid(params.getAll('regions'), value => /^\d{2}$/.test(value));
  query.departments = valid(params.getAll('departments'), value => /^(\d{2}|97[1-6]|2[AB])$/.test(value));
  query.workModes = valid(params.getAll('workModes'), (value): value is WorkMode => OFFER_WORK_MODES.includes(value as WorkMode));
  query.sites = valid(params.getAll('sites'), (value): value is OfferSite => SITES.includes(value as OfferSite));
  query.seniorities = valid(params.getAll('seniorities'), (value): value is Seniority => SENIORITIES.includes(value as Seniority));
  query.skills = valid(params.getAll('skills'), value => value.trim().length > 0 && value.length <= 60).slice(0, 10);
  const startBefore = params.get('startBefore');
  if (startBefore && /^\d{4}-\d{2}-\d{2}$/.test(startBefore)) {
    query.startBefore = startBefore;
  }
  query.rateKnown = params.get('rateKnown') === 'true' || undefined;
  query.unseenOnly = params.get('unseenOnly') === 'true' || undefined;
  query.matchesProfile = params.get('matchesProfile') === 'true' || undefined;
  query.hideSuspicious = params.get('hideSuspicious') === 'true' || undefined;
  const view = params.get('view') as OfferView;
  query.view = VIEWS.includes(view) ? view : 'FEED';
  const sort = params.get('sort') as OfferSort;
  query.sort = SORTS.includes(sort) ? sort : 'NEWEST';
  const page = Number(params.get('page'));
  query.page = Number.isInteger(page) && page > 0 ? page : 0;
  const size = Number(params.get('size'));
  query.size = OFFER_PAGE_SIZES.includes(size) ? size : OFFER_PAGE_SIZES[0];
  return query;
}

/** The page address of a query, without default values, so a plain feed keeps a clean URL. */
export function offerQueryToParams(query: OfferQuery): Params {
  const params: Params = {};
  const defaults = defaultOfferQuery();
  for (const [key, value] of Object.entries(query)) {
    const isDefault = value === defaults[key as keyof OfferQuery];
    const isEmpty = value === undefined || value === null || value === '' || value === false ||
      (Array.isArray(value) && value.length === 0);
    if (!isDefault && !isEmpty) {
      params[key] = Array.isArray(value) ? [...value] : String(value);
    }
  }
  return params;
}

/** The API request of a query: list filters repeat their key. */
export function offerQueryToHttpParams(query: OfferQuery): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(offerQueryToParams(query))) {
    for (const item of Array.isArray(value) ? value : [value]) {
      params = params.append(key, String(item));
    }
  }
  return params;
}

/** How many filters narrow the feed, for the badge on the filters toggle. */
export function countActiveFilters(filters: OfferFilters): number {
  let count = 0;
  for (const key of LIST_KEYS) {
    count += filters[key].length > 0 ? 1 : 0;
  }
  for (const key of NUMBER_KEYS) {
    count += filters[key] !== undefined ? 1 : 0;
  }
  return count + [filters.q, filters.startBefore, filters.rateKnown, filters.unseenOnly, filters.matchesProfile, filters.hideSuspicious]
    .filter(Boolean).length;
}

function valid<T extends string>(values: string[], accept: (value: string) => value is T): T[];
function valid(values: string[], accept: (value: string) => boolean): string[];
function valid(values: string[], accept: (value: string) => boolean): string[] {
  return [...new Set(values.flatMap(value => value.split(',')).filter(accept))];
}

/**
 * The query of a saved search, for the feed: its filters and sort as the server keeps them (unset values are null),
 * read like the page address so a stale or edited value is left out, on the feed's first page.
 */
export function offerQueryFromSaved(filters: Partial<Record<keyof OfferQuery, unknown>>): OfferQuery {
  const params: Params = {};
  for (const [key, value] of Object.entries(filters)) {
    if (value !== null && value !== undefined && key !== 'view' && key !== 'page') {
      params[key] = Array.isArray(value) ? value.map(String) : String(value);
    }
  }
  return offerQueryFromParams(convertToParamMap(params));
}
