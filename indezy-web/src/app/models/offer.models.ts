// Freelance offer feed: missions collected from the job boards (see docs/features.md#freelance-offer-feed)

import { WorkMode } from './project.models';

export type OfferSite =
  | 'FREE_WORK'
  | 'COLLECTIVE'
  | 'CHERRY_PICK'
  | 'LEHIBOU'
  | 'YEETS'
  | 'WORKDISPO'
  | 'NEXT_MONDAYS'
  | 'MINDQUEST'
  | 'FREELANCE_FACTORY'
  | 'LITTLE_BIG_CONNECTION'
  | 'SILKHOM'
  | 'LEGRATIN'
  | 'FREELANCE_INFORMATIQUE'
  | 'ESPACE_FREELANCE'
  | 'BEYOND_SKILLS_IT'
  | 'KOINO'
  | 'SIXIEME_HOMME';
export type Seniority = 'JUNIOR' | 'CONFIRMED' | 'SENIOR' | 'EXPERT';
export type OfferDatePrecision = 'EXACT' | 'DAY' | 'FIRST_SEEN';
export type OfferView = 'FEED' | 'SAVED' | 'HIDDEN';
export type OfferSort = 'NEWEST' | 'RATE' | 'DISTANCE' | 'MATCH';
export type OfferRateFit = 'MEETS' | 'CLOSE' | 'BELOW' | 'UNKNOWN';
export type OfferPlaceFit = 'REMOTE' | 'NEAR' | 'REACHABLE' | 'MOSTLY_REMOTE' | 'FAR' | 'UNKNOWN';

export const SENIORITIES: Seniority[] = ['JUNIOR', 'CONFIRMED', 'SENIOR', 'EXPERT'];
export const OFFER_WORK_MODES: WorkMode[] = ['ONSITE', 'HYBRID', 'REMOTE'];

/** How well an offer fits the profile, out of 100 (see docs/features.md#profile-match). */
export interface OfferMatch {
  score: number;
  /** The profile's skills the offer mentions, as the profile spells them. */
  skills: string[];
  /** Absent when the profile sets no target daily rate. */
  rate?: OfferRateFit;
  /** Absent when the profile names no city. */
  place?: OfferPlaceFit;
}

/** The same mission published on another site. */
export interface OfferCopy {
  id: number;
  site: OfferSite;
  siteName: string;
  url: string;
}

export interface Offer {
  id: number;
  site: OfferSite;
  siteName: string;
  url: string;
  title: string;
  company?: string;
  excerpt?: string;
  /** Only when one offer is asked for. */
  description?: string;
  rateMin?: number;
  rateMax?: number;
  city?: string;
  postalCode?: string;
  departmentCode?: string;
  departmentName?: string;
  regionName?: string;
  countryCode?: string;
  distanceKm?: number;
  workMode?: WorkMode;
  remoteDaysPerWeek?: number;
  durationMonths?: number;
  startDate?: string;
  startAsap?: boolean;
  seniority?: Seniority;
  skills: string[];
  publishedAt: string;
  datePrecision: OfferDatePrecision;
  expiresAt?: string;
  expired: boolean;
  saved: boolean;
  hidden: boolean;
  seen: boolean;
  importedProjectId?: number;
  /** The same mission on other sites, one copy per site. */
  alsoOn?: OfferCopy[];
  /** Absent when the profile names no skills. */
  match?: OfferMatch;
}

/** Filters of the feed saved under a name, with the offers found for them since they were last opened. */
export interface SavedOfferSearch {
  id: number;
  name: string;
  /** The filters as the server keeps them: unset values are null. */
  filters: Partial<Record<keyof OfferQuery, unknown>>;
  lastOpenedAt: string;
  newOffers: number;
}

export interface OfferAlerts {
  newOffers: number;
}

export interface OfferPage {
  content: Offer[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  /** Whether the profile's city is known, so distances can be shown and filtered on. */
  distanceAvailable: boolean;
  /** Whether the profile names skills, so offers are scored against it. */
  matchAvailable: boolean;
}

/** What the feed can be narrowed down by; empty lists and undefined values filter nothing. */
export interface OfferFilters {
  q?: string;
  rateMin?: number;
  rateMax?: number;
  rateKnown?: boolean;
  regions: string[];
  departments: string[];
  radiusKm?: number;
  workModes: WorkMode[];
  minRemoteDays?: number;
  durationMin?: number;
  durationMax?: number;
  /** ISO day (yyyy-MM-dd): missions starting as soon as possible or by this day. */
  startBefore?: string;
  publishedWithinDays?: number;
  sites: OfferSite[];
  seniorities: Seniority[];
  skills: string[];
  unseenOnly?: boolean;
  /** Offers scoring 60 or more against the profile. */
  matchesProfile?: boolean;
}

export interface OfferQuery extends OfferFilters {
  view: OfferView;
  sort: OfferSort;
  page: number;
  size: number;
}

export interface OfferState {
  offerId: number;
  saved: boolean;
  hidden: boolean;
  seen: boolean;
  importedProjectId?: number;
}

export interface OfferStateUpdate {
  saved?: boolean;
  hidden?: boolean;
  seen?: boolean;
}

export interface OfferSiteInfo {
  site: OfferSite;
  name: string;
  homeUrl: string;
  openOffers: number;
  lastSuccessAt?: string;
  failing: boolean;
}

export interface OfferFacet {
  value: string;
  offers: number;
}

export interface OfferRegion {
  regionCode: string;
  name: string;
  departments: { departmentCode: string; name: string }[];
}

export function emptyOfferFilters(): OfferFilters {
  return { regions: [], departments: [], workModes: [], sites: [], seniorities: [], skills: [] };
}
