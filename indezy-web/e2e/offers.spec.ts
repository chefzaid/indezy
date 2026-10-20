import { Page, Request, expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

interface MockOffer { id: number; [key: string]: unknown }

function offer(id: number, overrides: Record<string, unknown> = {}): MockOffer {
  return {
    id, site: 'FREE_WORK', siteName: 'Free-Work', url: `https://www.free-work.com/fr/tech-it/job-mission/java/${id}`,
    title: `Développeur Java ${id}`, company: 'Exemple Conseil', excerpt: 'Refonte d\'un SI bancaire en Java 21 et Spring Boot.',
    rateMin: 550, rateMax: 650, city: 'Courbevoie', departmentCode: '92', departmentName: 'Hauts-de-Seine', countryCode: 'FR',
    workMode: 'HYBRID', remoteDaysPerWeek: 2, durationMonths: 6, startDate: '2026-11-02', seniority: 'SENIOR',
    skills: ['Java', 'Spring Boot', 'Kafka'], publishedAt: new Date(Date.now() - 3 * 3600 * 1000).toISOString(),
    datePrecision: 'EXACT', expired: false, saved: false, hidden: false, seen: false, ...overrides
  };
}

/** A search saved four new offers ago. */
const savedSearch = {
  id: 5, name: 'Java remote', filters: { q: 'java', workModes: ['REMOTE'], sort: 'NEWEST', view: 'FEED', page: 0, size: 20 },
  lastOpenedAt: new Date(Date.now() - 86_400_000).toISOString(), newOffers: 4
};

/** The first offer is also published on Collective. */
const copy = { id: 11, site: 'COLLECTIVE', siteName: 'Collective', url: 'https://www.collective.work/job/11' };

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
async function mockFeed(page: Page, language: string, matchAvailable = true):
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
  await page.route('**/api/offers/*/state', route => {
    states.push(route.request().postDataJSON());
    return route.fulfill({ json: { offerId: 1, saved: true, hidden: false, seen: true } });
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
    return route.fulfill({ status: 201, json: { id: 6, name: body.name, filters: body.filters, lastOpenedAt: new Date().toISOString(), newOffers: 0 } });
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
async function settle(page: Page): Promise<void> {
  await page.evaluate(() => Promise.all(document.getAnimations().map(animation => animation.finished.catch(() => undefined))));
}

function isSearch(request: Request): boolean {
  return /\/api\/offers(\?.*)?$/.test(request.url());
}

test('browses, filters, saves, hides and imports freelance offers (en)', async ({ page }) => {
  const api = await mockFeed(page, 'en');

  await page.goto('/offers');
  await expect(page.getByRole('heading', { name: 'Freelance missions' })).toBeVisible();
  await expect(page.locator('[data-summary]')).toContainText('1242 open offers from 2 sites');
  const cards = page.locator('app-offer-card');
  await expect(cards).toHaveCount(2);
  const first = cards.first();
  await expect(first).toContainText('New');
  await expect(first.locator('[data-fact="place"]')).toContainText('Courbevoie (92)');
  await expect(first.locator('[data-fact="remote"]')).toContainText('Hybrid · 2 remote days a week');
  await expect(first.locator('[data-also-on]')).toContainText('Also on');
  await expect(first.locator('[data-also-on]').getByRole('link', { name: 'Collective' })).toHaveAttribute('href', copy.url);
  await expect(first.locator('[data-match]')).toHaveText(/80%/);
  await expect(first.locator('[data-match]')).toHaveAttribute('aria-label', /Your skills: Java, Kafka · Pays your target daily rate · Near you/);
  await expect(first.locator('.skill.matched')).toHaveText(['Java', 'Kafka']);
  await expect(page.locator('[data-match-hint]')).toHaveCount(0);

  await expect(page.locator('app-offer-market')).toHaveCount(0);
  await page.getByRole('button', { name: 'Market' }).click();
  const snapshot = page.locator('app-offer-market');
  await expect(snapshot.getByRole('heading', { name: 'Market snapshot' })).toBeVisible();
  await expect(snapshot.locator('[data-kpi="median"]')).toContainText('€600');
  await expect(snapshot.locator('[data-kpi="quartiles"]')).toContainText('€520 – €680');
  await expect(snapshot.locator('[data-market-target]')).toContainText('40% of the offers stating a rate pay your €650 target or more.');
  await expect(snapshot.locator('[data-market-rates] canvas')).toHaveAttribute('aria-label', /^Daily rates; < €500: 2 offers; €500 – €600: 10 offers/);
  await expect(snapshot.locator('[data-remote="hybrid"]')).toContainText('Hybrid 50% (21)');
  await expect(snapshot.locator('[data-market-intermediaries]')).toContainText('Exemple Conseil');
  const skillSearch = page.waitForRequest(request => isSearch(request) && request.url().includes('skills=Kafka'));
  await snapshot.getByRole('button', { name: 'Filter on Kafka (5 offers)' }).click();
  await skillSearch;
  await expect.poll(() => api.markets.at(-1)?.searchParams.getAll('skills')).toEqual(['Kafka']);
  await expect(snapshot.getByRole('button', { name: 'Filter on Kafka (5 offers)' })).toBeDisabled();

  const matchSearch = page.waitForRequest(request => isSearch(request) && request.url().includes('sort=MATCH'));
  await page.getByRole('combobox', { name: 'Sort by' }).click();
  await page.getByRole('option', { name: 'Best match' }).click();
  await matchSearch;
  const profileSearch = page.waitForRequest(request => isSearch(request) && request.url().includes('matchesProfile=true'));
  await page.getByRole('switch', { name: 'Matches my profile' }).click();
  await profileSearch;
  await expect(page).toHaveURL(/sort=MATCH/);

  const keywordSearch = page.waitForRequest(request => isSearch(request) && request.url().includes('q=kafka'));
  await page.getByLabel('Keywords').fill('kafka');
  await keywordSearch;
  await expect(page).toHaveURL(/q=kafka/);
  const rateSearch = page.waitForRequest(request => isSearch(request) && request.url().includes('rateMin=600'));
  await page.getByLabel('Minimum', { exact: true }).fill('600');
  await rateSearch;

  await expect(page.locator('[data-nav-badge]')).toHaveText('4');
  await expect(page.locator('[data-saved-search="5"] [data-new-count]')).toHaveText('4 new');
  await page.getByRole('button', { name: 'Save this search' }).click();
  await page.getByRole('dialog').getByLabel('Name').fill('Kafka 600+');
  await page.getByRole('dialog').getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.locator('.search-name', { hasText: 'Kafka 600+' })).toBeVisible();
  expect(api.saves).toEqual([expect.objectContaining({ name: 'Kafka 600+', filters: expect.objectContaining({ q: 'kafka', rateMin: 600 }) })]);

  await first.getByRole('button', { name: 'Save the offer' }).click();
  await expect(first.getByRole('button', { name: 'Remove from saved offers' })).toHaveAttribute('aria-pressed', 'true');
  expect(api.states).toContainEqual({ saved: true });

  await first.getByRole('button', { name: 'Show details' }).click();
  await expect(first.locator('[data-description]')).toContainText('Refonte complète du SI bancaire.');
  await expect(first).not.toContainText('New');

  await first.getByRole('button', { name: 'Add to pipeline' }).click();
  await expect(page.getByText('“Développeur Java 1” added to the pipeline (Contact stage)')).toBeVisible();
  await expect(first.getByRole('link', { name: 'In the pipeline' })).toHaveAttribute('href', '/projects/40');
  expect(api.imports).toEqual([1]);

  await cards.nth(1).getByRole('button', { name: 'Hide the offer' }).click();
  await expect(cards).toHaveCount(1);
  expect(api.states).toContainEqual({ hidden: true });

  const savedView = page.waitForRequest(request => isSearch(request) && request.url().includes('view=SAVED'));
  await page.locator('[data-view="SAVED"]').click();
  await savedView;
  await expect(cards).toHaveCount(2);
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('offers-en.png'), fullPage: true });

  const savedSearchOpened = page.waitForRequest(request => isSearch(request) && request.url().includes('workModes=REMOTE'));
  await page.locator('[data-saved-search="5"] .open-search').click();
  await savedSearchOpened;
  await expect(page).toHaveURL(/q=java/);
  await expect(page.locator('[data-saved-search="5"] [data-new-count]')).toHaveCount(0);
});

test('opens the filters on a phone and narrows the feed to full remote (fr)', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const api = await mockFeed(page, 'fr', false);

  await page.goto('/offers?publishedWithinDays=7');
  await expect(page.getByRole('heading', { name: 'Missions freelance' })).toBeVisible();
  await expect(page.locator('app-offer-card')).toHaveCount(2);
  await expect(page.locator('[data-match-hint]')).toContainText('Ajoutez vos compétences à votre profil');
  await expect(page.locator('[data-match-hint]').getByRole('link', { name: 'Compléter mon profil' })).toHaveAttribute('href', '/profile');
  await expect(page.locator('[data-match]')).toHaveCount(0);
  await expect(page.locator('app-offer-filters')).toBeHidden();
  const toggle = page.getByRole('button', { name: /Filtres/ });
  await expect(toggle).toContainText('1');
  await toggle.click();
  await expect(page.locator('app-offer-filters')).toBeVisible();
  await expect(page.getByText('Renseignez votre ville dans le profil')).toBeVisible();

  const remoteSearch = page.waitForRequest(request => isSearch(request) && request.url().includes('workModes=REMOTE'));
  await page.locator('[data-work-mode="REMOTE"]').click();
  await remoteSearch;
  await expect(page.locator('[data-empty]')).toContainText('Aucune offre ne correspond à ces filtres pour le moment.');
  expect(api.searches.at(-1)?.searchParams.get('publishedWithinDays')).toBe('7');

  await page.locator('[data-empty]').getByRole('button', { name: 'Effacer filtres' }).click();
  await expect(page).toHaveURL(/\/offers$/);
  await expect(page.locator('app-offer-card')).toHaveCount(2);

  await page.getByRole('button', { name: 'Marché' }).click();
  const snapshot = page.locator('app-offer-market');
  await expect(snapshot.getByRole('heading', { name: 'Aperçu du marché' })).toBeVisible();
  await expect(snapshot.locator('[data-kpi="median"]')).toContainText(/600\s€/);
  await expect(snapshot.locator('[data-market-remote]')).toContainText('Offres hybrides : 2,5 jours de télétravail par semaine en moyenne');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('offers-fr-mobile.png'), fullPage: true });
});

test('checks the job boards, keeps one out of the feed and reads another on request (en)', async ({ page }) => {
  const api = await mockFeed(page, 'en');

  await page.goto('/offers');
  await page.locator('[data-sites-link]').click();
  await expect(page).toHaveURL(/\/offers\/sites$/);
  await expect(page.getByRole('heading', { name: 'Job boards', level: 1 })).toBeVisible();
  await expect(page.locator('[data-sites-summary]')).toHaveText('3 job boards read for the feed · 1 failing');
  const yeets = page.locator('[data-site="YEETS"]');
  await expect(yeets.locator('[data-status]')).toHaveText('Failing');
  await expect(yeets).toContainText('3 failed readings in a row');
  await yeets.getByText('Last error').click();
  await expect(yeets.locator('code')).toHaveText('SiteResponseException: 503 from https://www.yeets.fr');
  const freeWork = page.locator('[data-site="FREE_WORK"]');
  await expect(freeWork.locator('[data-fact="last-success"]')).toHaveText('30 minutes ago');
  await expect(freeWork.locator('[data-fact="collected"]')).toHaveText('40 in 24 h · 210 in 7 days');
  await expect(freeWork.locator('[data-fact="last-run"]')).toHaveText('120 offers, 4 new');
  await expect(page.locator('[data-left-out="LINKEDIN"]')).toContainText('its robots.txt and user agreement forbid crawling');
  const malt = page.locator('[data-visit="MALT"]');
  await expect(malt).toContainText('Profile, then a call');
  await malt.getByRole('button', { name: 'Mark Malt as visited' }).click();
  await expect(malt.locator('[data-visited]')).toContainText('Visited now');
  await malt.getByRole('button', { name: 'Forget the visit of Malt' }).click();
  await expect(malt).toContainText('Not visited yet');
  expect(api.visits).toEqual(['PUT MALT', 'DELETE MALT']);

  await freeWork.getByRole('switch', { name: 'Show in my feed' }).click();
  await expect(page.getByText('Free-Work is left out of your feed')).toBeVisible();
  expect(api.exclusions).toEqual([{ site: 'FREE_WORK', excluded: true }]);
  await expect(freeWork).toContainText('Its offers are left out of your feed, saved searches and market snapshot.');

  await freeWork.getByRole('button', { name: 'Read Free-Work now' }).click();
  await expect(page.getByText('Reading Free-Work: update the page in a minute to see what it brought.')).toBeVisible();
  await expect(freeWork.getByRole('button', { name: 'Read Free-Work now' })).toBeDisabled();
  await expect(freeWork.locator('[data-refresh-hint]')).toHaveText('Can be read again in 15 minutes.');
  await page.locator('[data-site="COLLECTIVE"]').getByRole('button', { name: 'Read Collective now' }).click();
  await expect(page.getByText('Collective can be read again in 9 minutes.')).toBeVisible();
  await expect(page.locator('[data-site="COLLECTIVE"] [data-refresh-hint]')).toHaveText('Can be read again in 9 minutes.');
  expect(api.refreshes).toEqual(['FREE_WORK', 'COLLECTIVE']);
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('offer-sites-en.png'), fullPage: true });
});

test('reads the job boards on a phone (fr)', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockFeed(page, 'fr');

  await page.goto('/offers');
  await page.locator('[data-sites-to-visit-link]').click();
  await expect(page).toHaveURL(/\/offers\/sites#sites-to-visit$/);
  await expect(page.getByRole('heading', { name: "Sites d'offres", level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Sites à consulter vous-même' })).toBeInViewport();
  await expect(page.locator('[data-site="YEETS"] [data-status]')).toHaveText('En échec');
  await expect(page.locator('[data-site="FREE_WORK"] [data-fact="collected"]')).toHaveText('40 en 24 h · 210 en 7 jours');
  await expect(page.getByRole('heading', { name: 'Sites écartés' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('offer-sites-fr-mobile.png'), fullPage: true });
  await page.getByRole('link', { name: 'Retour aux missions' }).click();
  await expect(page).toHaveURL(/\/offers$/);
});

test('hides a publisher from an offer and shows it again from the profile (en)', async ({ page }) => {
  await mockFeed(page, 'en');
  const blocks: { id: number; name: string }[] = [];
  const calls: string[] = [];
  await page.route('**/api/offers/blocked-publishers', route => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: blocks });
    }
    const block = { id: 8 + blocks.length, name: route.request().postDataJSON().name };
    blocks.push(block);
    calls.push(`block ${block.name}`);
    return route.fulfill({ json: block });
  });
  await page.route('**/api/offers/blocked-publishers/*', route => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1));
    calls.push(`unblock ${id}`);
    blocks.splice(blocks.findIndex(block => block.id === id), 1);
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/users/profile', route => route.fulfill({ json: {
    id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test', targetDailyRate: 600
  } }));
  await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));

  await page.goto('/offers');
  const cards = page.locator('app-offer-card');
  await expect(cards).toHaveCount(2);
  await cards.first().getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Hide every offer from Exemple Conseil' }).click();
  await expect(page.getByText('Offers from Exemple Conseil are hidden from your feed. Bring them back from your profile.')).toBeVisible();
  await expect(cards).toHaveCount(1);
  await expect(cards.first()).toContainText('Data engineer Spark');

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Offer feed' }).click();
  await expect(page.locator('[data-block="Exemple Conseil"]')).toBeVisible();
  await page.getByLabel('Intermediary or client').fill('Acme');
  await page.getByRole('button', { name: 'Hide its offers' }).click();
  await expect(page.locator('[data-block="Acme"]')).toBeVisible();
  await page.getByRole('button', { name: 'Show offers from Exemple Conseil again' }).click();
  await expect(page.locator('[data-block="Exemple Conseil"]')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Choose the job boards of your feed' })).toHaveAttribute('href', '/offers/sites');
  expect(calls).toEqual(['block Exemple Conseil', 'block Acme', 'unblock 8']);
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('blocked-publishers-en.png'), fullPage: true });
});

test('moves through the feed and acts on offers with the keyboard (en)', async ({ page }) => {
  const api = await mockFeed(page, 'en');

  await page.goto('/offers');
  const items = page.locator('[data-offer-item]');
  await expect(items).toHaveCount(2);
  await items.first().focus();
  await page.keyboard.press('j');
  await expect(items.nth(1)).toBeFocused();
  await page.keyboard.press('s');
  await expect.poll(() => api.states).toContainEqual({ saved: true });
  await page.keyboard.press('k');
  await expect(items.first()).toBeFocused();
  await page.keyboard.press('d');
  await expect(items.first().locator('[data-description]')).toContainText('Refonte complète du SI bancaire.');
  await page.keyboard.press('h');
  await expect(items).toHaveCount(1);
  await expect(items.first()).toBeFocused();
  expect(api.states).toContainEqual({ hidden: true });

  await page.keyboard.press('Alt+Shift+H');
  await expect(page.getByRole('dialog').locator('[data-feed-shortcuts]')).toContainText('Add the offer to the pipeline');
});

test('shows the best new missions on the dashboard (en)', async ({ page }) => {
  const api = await mockFeed(page, 'en');
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));

  await page.goto('/dashboard');
  const widget = page.locator('app-dashboard-offers');
  await expect(widget.getByRole('heading', { name: 'Your best new missions' })).toBeVisible();
  await expect(widget.locator('[data-dashboard-offer]')).toHaveCount(2);
  await expect(widget.locator('[data-dashboard-offer="1"]')).toContainText('80%');
  expect(api.searches.some(url => url.searchParams.get('sort') === 'MATCH' && url.searchParams.get('unseenOnly') === 'true')).toBe(true);
  await widget.getByRole('link', { name: 'Open the feed' }).click();
  await expect(page).toHaveURL(/\/offers\?sort=MATCH&unseenOnly=true$/);
});
