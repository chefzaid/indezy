// Freelance offer feed: missions collected from the job boards (see docs/features.md#freelance-offer-feed)

import { DocumentLanguage } from './activity-report.models';
import { ProjectDto, WorkMode } from './project.models';

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
export type OfferView = 'FEED' | 'SAVED' | 'HIDDEN' | 'APPLIED';
/** How the workspace applied to an offer. */
export type ApplicationChannel = 'SITE_FORM' | 'EMAIL' | 'PHONE';
export type OfferSort = 'NEWEST' | 'RATE' | 'DISTANCE' | 'MATCH';
export type OfferRateFit = 'MEETS' | 'CLOSE' | 'BELOW' | 'UNKNOWN';
export type OfferPlaceFit = 'REMOTE' | 'NEAR' | 'REACHABLE' | 'MOSTLY_REMOTE' | 'FAR' | 'UNKNOWN';
/** Why an offer looks like a fake or spam listing (see docs/features.md#suspicious-offers). */
export type OfferSuspicion = 'NO_PUBLISHER_NO_DESCRIPTION' | 'RATE_OUTSIDE_MARKET' | 'SAME_TEXT_MANY_PUBLISHERS' | 'KNOWN_FAKE_PUBLISHER';
/** When the mission starts against when the freelance is available: once available, up to two weeks before, earlier. */
export type OfferAvailabilityFit = 'READY' | 'SOON' | 'TOO_EARLY' | 'UNKNOWN';

export const SENIORITIES: Seniority[] = ['JUNIOR', 'CONFIRMED', 'SENIOR', 'EXPERT'];
export const OFFER_WORK_MODES: WorkMode[] = ['ONSITE', 'HYBRID', 'REMOTE'];
export const APPLICATION_CHANNELS: ApplicationChannel[] = ['SITE_FORM', 'EMAIL', 'PHONE'];

/** How well an offer fits the profile, out of 100 (see docs/features.md#profile-match). */
export interface OfferMatch {
  score: number;
  /** The profile's skills the offer mentions, as the profile spells them. */
  skills: string[];
  /** Absent when the profile sets no target daily rate. */
  rate?: OfferRateFit;
  /** Absent when the profile names no city. */
  place?: OfferPlaceFit;
  /** Absent when the profile states no availability date. */
  availability?: OfferAvailabilityFit;
}

/** The workspace applied to the mission: on which day (yyyy-MM-dd) and how. */
export interface OfferApplication {
  appliedOn: string;
  via: ApplicationChannel;
}

/** Recording an application: the pipeline's steps are titled in `language`. */
export interface OfferApplicationRequest {
  appliedOn: string;
  via: ApplicationChannel;
  /** A follow-up is planned that many days later (1 to 60). */
  followUpInDays?: number;
  language?: DocumentLanguage;
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
  /** The workspace's private note on the mission. */
  note?: string;
  application?: OfferApplication;
  /** Absent without a stated rate, a skill or ten comparable offers. */
  rateMarket?: OfferRateMarket;
  /** Why it looks like a fake or spam listing; absent when it does not. */
  suspicion?: OfferSuspicion[];
  /** The intermediaries publishing the mission and the spread of their best rates; absent when one does. */
  publisherSpread?: { publishers: number; rateMin?: number; rateMax?: number };
  /** What its site changed on it and how often the mission came back; absent without either. */
  history?: OfferHistory;
}

/** What changed on an offer: rates as "min|max" (either side empty), starts as an ISO day or "ASAP", descriptions without values. */
export interface OfferChange {
  field: 'RATE' | 'START' | 'DESCRIPTION';
  before?: string;
  after?: string;
  changedAt: string;
}

/** See docs/features.md#mission-history. */
export interface OfferHistory {
  /** References the mission was published under in 60 days, when more than one. */
  publishedTimes?: number;
  /** The latest first. */
  changes?: OfferChange[];
}

/** What a publisher puts in the feed (see docs/features.md#publisher-page). */
export interface OfferPublisher {
  key: string;
  name: string;
  openMissions: number;
  medianRate?: number;
  /** How long its open offers have been published, the median, in days. */
  medianOpenDays?: number;
  /** Its missions of the last 90 days, each once per site, and how many it published again under a new reference. */
  missions: number;
  republished: number;
  sites: { site: OfferSite; siteName: string; offers: number }[];
  /** The workspace's client named like it. */
  client?: { id: number; name: string; intermediary: boolean };
  /** The workspace's block of it, absent when its offers are in the feed. */
  blockId?: number;
  offers: { id: number; title: string; siteName: string; rateMin?: number; rateMax?: number; city?: string; publishedAt: string }[];
}

/**
 * The offer's daily rate (the middle of its range) against the median of the open offers naming the same skill first,
 * in its region, or in all of France when `regionName` is absent.
 */
export interface OfferRateMarket {
  /** Above (positive) or below the median, rounded. */
  percent: number;
  median: number;
  /** The offers the median is taken over, one per mission. */
  offers: number;
  skill: string;
  regionName?: string;
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

/** A search suggested from the profile and the pipeline, with what it finds in the feed now. */
export interface OfferSearchSuggestion {
  /** Tells its filters apart: the same filters, saved or dismissed, are not suggested again. */
  key: string;
  /** The filters as the server keeps them: unset values are null. */
  filters: Partial<Record<keyof OfferQuery, unknown>>;
  /** The profile's city when the filters measure a distance from it, or the region they keep. */
  place?: string | null;
  offers: number;
  /** Offers scoring 60 or more against the profile. */
  goodMatches: number;
  /** Offers published in the last seven days. */
  newThisWeek: number;
}

/** A profile field suggestions are drawn from. */
export type OfferProfileField = 'SKILLS' | 'POSITION' | 'CITY' | 'TARGET_RATE';

export interface OfferSearchSuggestions {
  /** At most five, best first. */
  suggestions: OfferSearchSuggestion[];
  /** The profile fields left empty, which would bring more suggestions. */
  missing: OfferProfileField[];
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
  /** Leave out the offers that look like fake or spam listings. */
  hideSuspicious?: boolean;
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
  note?: string;
  application?: OfferApplication;
}

/** What adding several offers to the pipeline did: each offer with the opportunity of its mission. */
export interface OfferBulkImport {
  imported: { offerId: number; projectId: number }[];
  alreadyInPipeline: { offerId: number; projectId: number }[];
  /** Offers no longer collected. */
  missing: number[];
}

/** What the link of an offer gave: see docs/features.md#from-an-offer-link. */
export type OfferLinkStatus =
  | 'FOUND'
  | 'IN_PIPELINE'
  | 'READING'
  | 'NOT_COLLECTED'
  | 'UNSUPPORTED_SITE'
  | 'NOT_FREELANCE'
  | 'EXPIRED'
  | 'GONE'
  | 'SITE_UNAVAILABLE';

/** The opportunity an offer would make, not created yet: its client and source are unset when the workspace has none of that name. */
export interface OfferDraft {
  project: ProjectDto;
  clientName: string;
  sourceName: string;
}

export interface OfferLink {
  status: OfferLinkStatus;
  siteName?: string;
  /** With FOUND and IN_PIPELINE. */
  offer?: Offer;
  /** The opportunity of the mission, with IN_PIPELINE. */
  projectId?: number;
  /** With FOUND. */
  draft?: OfferDraft;
  /** When to look again (READING), or when the site can be read on request again (NOT_COLLECTED). */
  retryAt?: string;
  /** The sites the feed reads, with UNSUPPORTED_SITE. */
  sites?: string[];
}

export interface OfferStateUpdate {
  saved?: boolean;
  hidden?: boolean;
  seen?: boolean;
  /** A blank note clears it. */
  note?: string;
}

export interface OfferSiteInfo {
  site: OfferSite;
  name: string;
  homeUrl: string;
  openOffers: number;
  lastSuccessAt?: string;
  failing: boolean;
  /** The workspace keeps the site out of its feed. */
  excluded?: boolean;
}

export type OfferSiteStatus = 'OK' | 'FAILING' | 'RUNNING' | 'WAITING';

/** How one job board's collection goes: see the server's OfferSiteHealthDto. */
export interface OfferSiteHealthEntry {
  site: OfferSite;
  name: string;
  homeUrl: string;
  status: OfferSiteStatus;
  lastRunAt?: string | null;
  lastSuccessAt?: string | null;
  nextRunAt?: string | null;
  lastError?: string | null;
  consecutiveFailures: number;
  lastFound?: number | null;
  lastNew?: number | null;
  collectedDay: number;
  collectedWeek: number;
  openOffers: number;
  newestOfferAt?: string | null;
  excluded: boolean;
  /** When it can be read on request again; absent when it can now. */
  refreshAvailableAt?: string | null;
}

/** Whether a site Indezy does not read is to visit, a matching service, or left out. */
export type OfferLeftOutKind = 'VISIT' | 'MATCHING' | 'OTHER';
/** What seeing a site's missions takes. */
export type OfferLeftOutAccess = 'MEMBERSHIP' | 'SIGN_IN' | 'REFERRAL' | 'CRAWLERS' | 'PROFILE';

export interface OfferLeftOutSite {
  site: string;
  name: string;
  homeUrl: string;
  kind: OfferLeftOutKind;
  access?: OfferLeftOutAccess | null;
  /** When the workspace last visited it, if it did. */
  visitedAt?: string | null;
}

export interface OfferSiteHealth {
  sites: OfferSiteHealthEntry[];
  leftOut: OfferLeftOutSite[];
  refreshEnabled: boolean;
}

export interface OfferFacet {
  value: string;
  offers: number;
}

/** The market the current filters describe: see the server's OfferMarketDto. */
export interface OfferMarket {
  offers: number;
  rates: {
    stated: number;
    firstQuartile?: number | null;
    median?: number | null;
    thirdQuartile?: number | null;
    target?: number | null;
    /** Percent of the offers stating a rate whose best rate reaches the target. */
    meetTarget?: number | null;
  };
  /** Daily rates from `from` included to `to` excluded; the first and last bands may be open. */
  rateBins: { from?: number | null; to?: number | null; offers: number }[];
  remote: { remote: number; hybrid: number; onsite: number; unstated: number; hybridRemoteDays?: number | null };
  skills: OfferFacet[];
  intermediaries: OfferFacet[];
  /** Offers published each of the last days, oldest first. */
  perDay: { day: string; offers: number }[];
}

/** One night of a skill's market: its median daily rate and open offers. */
export interface OfferTrendNight {
  day: string;
  median: number;
  offers: number;
}

/** How a skill's market moved (see docs/features.md#market-trends). */
export interface OfferTrend {
  /** The profile's skills, to choose from. */
  skills: string[];
  skill?: string;
  /** Absent in all of France. */
  regionName?: string;
  /** Absent without history. */
  latest?: OfferTrendNight;
  /** Over 30, 90 and 365 days; a percentage is absent without a night that far back. */
  changes?: { days: number; medianPercent?: number; offersPercent?: number }[];
  nights?: OfferTrendNight[];
}

export interface OfferRegion {
  regionCode: string;
  name: string;
  departments: { departmentCode: string; name: string }[];
}

/** A publisher the workspace keeps out of its feed. */
export interface OfferPublisherBlock {
  id: number;
  name: string;
}

/** How publishers are told apart, as the server does: case, accents and punctuation aside. */
export function publisherKey(name: string | undefined | null): string {
  return (name ?? '').normalize('NFD').replace(/\p{M}+/gu, '').toLowerCase().replace(/[^\p{L}\p{N}+#.]+/gu, ' ').trim();
}

export function emptyOfferFilters(): OfferFilters {
  return { regions: [], departments: [], workModes: [], sites: [], seniorities: [], skills: [] };
}
