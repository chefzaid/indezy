import { expect, test } from '@playwright/test';
import { emptyDashboardStats } from './helpers/workspace';
import { copy, isSearch, mockFeed, settle } from './helpers/offer-feed';

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
  // The four views fit the phone's width.
  for (const view of ['FEED', 'SAVED', 'APPLIED', 'HIDDEN']) {
    const box = await page.locator(`[data-view="${view}"]`).boundingBox();
    expect(box && box.x >= 0 && box.x + box.width <= 390).toBe(true);
  }
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
