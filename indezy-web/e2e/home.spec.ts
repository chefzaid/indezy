import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

// A French browser stays at the root; an English one is taken to /en (below).
test.use({ locale: 'fr-FR' });

for (const theme of ['light', 'dark'] as const) {
  for (const width of [1280, 390]) {
    test(`presents Indezy to a signed-out visitor (${theme}, ${width}px)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.emulateMedia({ colorScheme: theme });
      const keycloak: string[] = [];
      page.on('request', request => {
        if (request.url().includes('keycloak') || request.url().includes('/oauth2/')) {
          keycloak.push(request.url());
        }
      });
      await page.goto('/');
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Trouvez la mission, décrochez-la au bon TJM, puis menez-la.');
      await expect(page).toHaveTitle(/Trouvez la mission/);
      await expect(page.locator('[data-feature]')).toHaveCount(6);
      // The screenshot of the visitor's theme loads.
      const screen = page.locator('[data-screen="kanban"] img');
      await screen.scrollIntoViewIfNeeded();
      await expect.poll(() => screen.evaluate(image => (image as HTMLImageElement).currentSrc)).toContain(`kanban-fr-${theme}.jpg`);
      await expect(page.getByRole('button', { name: 'Se connecter' }).first()).toBeVisible();
      await page.locator('[data-question="sites"]').click();
      await expect(page.locator('[data-question="sites"]')).toContainText('Free-Work');
      const audit = await new AxeBuilder({ page }).withTags(WCAG).analyze();
      expect(audit.violations.map(violation => violation.id)).toEqual([]);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      expect(page.url()).toMatch(/\/$/);
      expect(keycloak).toEqual([]);

      await page.getByRole('button', { name: 'EN', exact: true }).click();
      await expect(page).toHaveURL(/\/en$/);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText('Find the mission, win it at the right rate, then run it.');
      await expect(page.locator('link[rel="canonical"]')).toHaveAttribute('href', /\/en$/);
    });
  }
}

test.describe('in an English browser', () => {
  test.use({ locale: 'en-GB' });

  test('takes a first visit to the English page, and keeps a language chosen', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/en$/);
    await expect(page).toHaveTitle(/Find the mission/);
    await page.getByRole('button', { name: 'FR', exact: true }).click();
    await expect(page).toHaveURL(/\/$/);
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('Trouvez la mission, décrochez-la au bon TJM, puis menez-la.');
  });
});

test('opens the dashboard for a signed-in visitor, from the page in their language', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.goto('/en');
  // The page shows without the app's menu.
  await expect(page.locator('mat-sidenav')).toHaveCount(0);
  await page.locator('[data-open-dashboard]').click();
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('still opens a mission from an old shared link', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.route('**/api/projects/12', route => route.fulfill({ json: { id: 12, role: 'Java developer', status: 'CONTACT', dailyRate: 600 } }));
  await page.goto('/?project=12');
  await expect(page).toHaveURL(/\/projects\/12$/);
});

test('gives search engines the presentation page only', async ({ request }) => {
  const robots = await (await request.get('/robots.txt')).text();
  expect(robots).toContain('Allow: /$');
  expect(robots).toContain('Disallow: /\n');
  const sitemap = await (await request.get('/sitemap.xml')).text();
  expect(sitemap).toContain('<loc>https://indezy.swirlit.dev/en</loc>');
});
