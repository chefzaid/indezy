import { Page, Request } from '@playwright/test';
import { mockWorkspaceSession } from './workspace';

export interface MockOffer { id: number; [key: string]: unknown }

export function offer(id: number, overrides: Record<string, unknown> = {}): MockOffer {
  return {
    id, site: 'FREE_WORK', siteName: 'Free-Work', url: `https://www.free-work.com/fr/tech-it/job-mission/java/${id}`,
    title: `Développeur Java ${id}`, company: 'Exemple Conseil', excerpt: 'Refonte d\'un SI bancaire en Java 21 et Spring Boot.',
    rateMin: 550, rateMax: 650, city: 'Courbevoie', departmentCode: '92', departmentName: 'Hauts-de-Seine', countryCode: 'FR',
    workMode: 'HYBRID', remoteDaysPerWeek: 2, durationMonths: 6, startDate: '2026-11-02', seniority: 'SENIOR',
    commute: { carMinutes: 18, carRushMinutes: 26, transitMinutes: 42, transitRushMinutes: 48 },
    skills: ['Java', 'Spring Boot', 'Kafka'], publishedAt: new Date(Date.now() - 3 * 3600 * 1000).toISOString(),
    datePrecision: 'EXACT', expired: false, saved: false, hidden: false, seen: false, ...overrides
  };
}

/** A search saved four new offers ago. */
/** A saved search's emails, off unless given. */
export function searchEmails(overrides: Record<string, unknown> = {}) {
  return { emails: { frequency: 'OFF', hour: null, language: null, stoppedAt: null, ...overrides }, emailsAvailable: true };
}

export const savedSearch = {
  id: 5, name: 'Java remote', filters: { q: 'java', workModes: ['REMOTE'], sort: 'NEWEST', view: 'FEED', page: 0, size: 20 },
  lastOpenedAt: new Date(Date.now() - 86_400_000).toISOString(), newOffers: 4, ...searchEmails()
};

/** The first offer is also published on Collective. */
export const copy = { id: 11, site: 'COLLECTIVE', siteName: 'Collective', url: 'https://www.collective.work/job/11' };

const sites = [
  { site: 'FREE_WORK', name: 'Free-Work', homeUrl: 'https://www.free-work.com', openOffers: 812, lastSuccessAt: new Date().toISOString(), failing: false },
  { site: 'COLLECTIVE', name: 'Collective', homeUrl: 'https://www.collective.work', openOffers: 430, failing: false }
];

const regions = [
  { regionCode: '11', name: 'Île-de-France', departments: [{ departmentCode: '75', name: 'Paris' }, { departmentCode: '92', name: 'Hauts-de-Seine' }] },
  { regionCode: '84', name: 'Auvergne-Rhône-Alpes', departments: [{ departmentCode: '69', name: 'Rhône' }] }
];

/** The market of the feed: a target rate of 650 € reached by 40 percent of the offers stating a rate. */
const market = {
  offers: 42,
  rates: { stated: 30, firstQuartile: 520, median: 600, thirdQuartile: 680, target: 650, meetTarget: 40 },
  rateBins: [{ to: 500, offers: 2 }, { from: 500, to: 600, offers: 10 }, { from: 600, to: 700, offers: 15 }, { from: 700, offers: 3 }],
  remote: { remote: 10, hybrid: 21, onsite: 7, unstated: 4, hybridRemoteDays: 2.5 },
  skills: [{ value: 'Java', offers: 20 }, { value: 'Kafka', offers: 5 }],
  intermediaries: [{ value: 'Exemple Conseil', offers: 6 }, { value: 'Acme', offers: 3 }],
  perDay: Array.from({ length: 30 }, (_, index) => ({
    day: new Date(Date.now() - (29 - index) * 86_400_000).toISOString().slice(0, 10), offers: index % 7
  }))
};

/** The job boards: Yeets fails, Free-Work and Collective work; Collective was read on request a few minutes ago. */
function siteHealth(): Record<string, unknown> {
  const minutes = (count: number): string => new Date(Date.now() + (count + Math.sign(count) / 4) * 60_000).toISOString();
  const entry = (site: string, name: string, overrides: Record<string, unknown> = {}): Record<string, unknown> => ({
    site, name, homeUrl: `https://www.${name.toLowerCase()}.com`, status: 'OK', lastRunAt: minutes(-30), lastSuccessAt: minutes(-30),
    nextRunAt: minutes(30), consecutiveFailures: 0, lastFound: 120, lastNew: 4, collectedDay: 40, collectedWeek: 210, openOffers: 812,
    newestOfferAt: minutes(-90), excluded: false, ...overrides
  });
  return {
    sites: [
      entry('YEETS', 'Yeets', { status: 'FAILING', consecutiveFailures: 3, lastSuccessAt: minutes(-3000), lastFound: null,
        lastError: 'SiteResponseException: 503 from https://www.yeets.fr' }),
      entry('COLLECTIVE', 'Collective', { collectedDay: 12, openOffers: 430 }),
      entry('FREE_WORK', 'Free-Work')
    ],
    leftOut: [{ site: 'HITECHPROS', name: 'HiTechPros', homeUrl: 'https://www.hitechpros.com', kind: 'VISIT', access: 'MEMBERSHIP' },
      { site: 'MALT', name: 'Malt', homeUrl: 'https://www.malt.fr', kind: 'MATCHING', access: 'PROFILE' },
      { site: 'LINKEDIN', name: 'LinkedIn', homeUrl: 'https://www.linkedin.com', kind: 'OTHER' }],
    refreshEnabled: true
  };
}

/** Answers the feed's API like the server would, and records the searches and state changes. */
export async function mockFeed(page: Page, language: string, matchAvailable = true):
  Promise<{ searches: URL[]; states: unknown[]; imports: number[]; saves: Record<string, unknown>[]; markets: URL[];
    exclusions: unknown[]; refreshes: string[]; visits: string[] }> {
  const searches: URL[] = [];
  const markets: URL[] = [];
  const exclusions: unknown[] = [];
  const refreshes: string[] = [];
  const visits: string[] = [];
  const states: unknown[] = [];
  const imports: number[] = [];
  const saves: Record<string, unknown>[] = [];
  await mockWorkspaceSession(page, language);
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
  await page.route('**/api/offers/sites', route => route.fulfill({ json: sites }));
  await page.route('**/api/offers/regions', route => route.fulfill({ json: regions }));
  await page.route('**/api/offers/skills**', route => route.fulfill({ json: [{ value: 'Kafka', offers: 120 }] }));
  await page.route('**/api/offers/sites/health', route => route.fulfill({ json: siteHealth() }));
  await page.route('**/api/offers/sites/*/exclusion', route => {
    const site = new URL(route.request().url()).pathname.split('/').at(-2);
    exclusions.push({ site, ...route.request().postDataJSON() });
    return route.fulfill({ json: { site, ...route.request().postDataJSON() } });
  });
  await page.route('**/api/offers/sites/left-out/*/visit', route => {
    const site = new URL(route.request().url()).pathname.split('/').at(-2) as string;
    visits.push(`${route.request().method()} ${site}`);
    return route.fulfill({ json: { site, visitedAt: route.request().method() === 'PUT' ? new Date().toISOString() : null } });
  });
  await page.route('**/api/offers/sites/*/refresh', route => {
    const site = new URL(route.request().url()).pathname.split('/').at(-2) as string;
    refreshes.push(site);
    return site === 'COLLECTIVE'
      ? route.fulfill({ status: 429, headers: { 'Retry-After': '540', 'Access-Control-Expose-Headers': 'Retry-After' }, json: { message: 'Collective is read on request at most once every 15 minutes' } })
      : route.fulfill({ status: 202, json: { site, refreshAvailableAt: new Date(Date.now() + 15.25 * 60_000).toISOString() } });
  });
  await page.route('**/api/offers/market**', route => {
    markets.push(new URL(route.request().url()));
    return route.fulfill({ json: market });
  });
  // Registered after the snapshot's, so it answers the trends.
  await page.route('**/api/offers/market/trends**', route => route.fulfill({ json: {
    skills: ['Java', 'Kafka'], skill: 'Java', latest: { day: new Date().toISOString().slice(0, 10), median: 630, offers: 120 },
    changes: [{ days: 30, medianPercent: 5, offersPercent: -25 }, { days: 90, medianPercent: 0, offersPercent: 12 }, { days: 365 }]
  } }));
  await page.route('**/api/offers/*/state', route => {
    const body = route.request().postDataJSON();
    states.push(body);
    return route.fulfill({ json: { offerId: 1, saved: true, hidden: false, seen: true, note: body.note?.trim() || undefined } });
  });
  await page.route('**/api/offers/*/import', route => {
    imports.push(Number(new URL(route.request().url()).pathname.split('/').at(-2)));
    return route.fulfill({ status: 201, json: { id: 40, role: 'Développeur Java 1' } });
  });
  await page.route('**/api/offers/searches/alerts', route => route.fulfill({ json: { newOffers: 4 } }));
  await page.route('**/api/offers/searches/*/open', route => route.fulfill({ json: { ...savedSearch, newOffers: 0 } }));
  await page.route(/\/api\/offers\/searches$/, route => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: [savedSearch] });
    }
    const body = route.request().postDataJSON();
    saves.push(body);
    return route.fulfill({ status: 201, json: { id: 6, name: body.name, filters: body.filters, lastOpenedAt: new Date().toISOString(), newOffers: 0,
      ...searchEmails() } });
  });
  await page.route(/\/api\/offers\/\d+$/, route =>
    route.fulfill({ json: offer(1, { description: 'Contexte\nRefonte complète du SI bancaire.\n\nProfil\n5 ans d\'expérience.' }) }));
  await page.route(/\/api\/offers(\?.*)?$/, route => {
    const url = new URL(route.request().url());
    searches.push(url);
    const filtered = url.searchParams.has('workModes') && !url.searchParams.getAll('workModes').includes('HYBRID');
    const match = (score: number, skills: string[], place: string) => matchAvailable ? { score, skills, rate: 'MEETS', place } : undefined;
    const content = filtered ? [] : [offer(1, { alsoOn: [copy], match: match(80, ['Java', 'Kafka'], 'NEAR') }), offer(2, {
      title: 'Data engineer Spark', company: 'Données & Cie', site: 'COLLECTIVE', siteName: 'Collective', rateMin: undefined, rateMax: 700,
      workMode: 'REMOTE',
      remoteDaysPerWeek: 5, seen: true, skills: ['Spark'], match: match(40, [], 'REMOTE') })];
    return route.fulfill({ json: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, distanceAvailable: false,
      matchAvailable } });
  });
  return { searches, states, imports, saves, markets, exclusions, refreshes, visits };
}

/** Lets the router's cross-fade between two addresses finish before a screenshot. */
export async function settle(page: Page): Promise<void> {
  await page.evaluate(() => Promise.all(document.getAnimations().map(animation => animation.finished.catch(() => undefined))));
}

export function isSearch(request: Request): boolean {
  return /\/api\/offers(\?.*)?$/.test(request.url());
}
