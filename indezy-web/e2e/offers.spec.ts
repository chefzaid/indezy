import { Page, Request, expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

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

/** Answers the feed's API like the server would, and records the searches and state changes. */
async function mockFeed(page: Page, language: string):
  Promise<{ searches: URL[]; states: unknown[]; imports: number[]; saves: Record<string, unknown>[] }> {
  const searches: URL[] = [];
  const states: unknown[] = [];
  const imports: number[] = [];
  const saves: Record<string, unknown>[] = [];
  await mockWorkspaceSession(page, language);
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
  await page.route('**/api/offers/sites', route => route.fulfill({ json: sites }));
  await page.route('**/api/offers/regions', route => route.fulfill({ json: regions }));
  await page.route('**/api/offers/skills**', route => route.fulfill({ json: [{ value: 'Kafka', offers: 120 }] }));
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
    const content = filtered ? [] : [offer(1, { alsoOn: [copy] }), offer(2, { title: 'Data engineer Spark', site: 'COLLECTIVE', siteName: 'Collective',
      rateMin: undefined, rateMax: 700, workMode: 'REMOTE', remoteDaysPerWeek: 5, seen: true })];
    return route.fulfill({ json: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, distanceAvailable: false } });
  });
  return { searches, states, imports, saves };
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
  const api = await mockFeed(page, 'fr');

  await page.goto('/offers?publishedWithinDays=7');
  await expect(page.getByRole('heading', { name: 'Missions freelance' })).toBeVisible();
  await expect(page.locator('app-offer-card')).toHaveCount(2);
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
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('offers-fr-mobile.png'), fullPage: true });
});
