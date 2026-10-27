import AxeBuilder from '@axe-core/playwright';
import { Page, expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

/** The app's main pages, each audited once its content is shown. */
const PAGES = ['/dashboard', '/projects', '/projects/42', '/projects/new', '/clients', '/clients/3', '/contacts', '/contacts/2',
  '/sources', '/calendar', '/archive', '/review', '/profile', '/offers', '/welcome', '/p/workspace-test', '/admin',
  '/invoices', '/documents'];

/** The WCAG 2.1 A and AA rules a page breaks, one line each, empty when it breaks none. */
async function violations(page: Page): Promise<string[]> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  return results.violations.flatMap(violation => violation.nodes.map(node =>
    `${violation.id} (${violation.impact}): ${node.target.join(' ')}`));
}

async function settled(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle');
  await expect(page.locator('mat-spinner, mat-progress-bar')).toHaveCount(0);
}

for (const { language, theme, phone } of [
  { language: 'en', theme: 'light', phone: false },
  { language: 'fr', theme: 'dark', phone: true }
]) {
  test(`meets WCAG 2.1 AA on every main page (${language}, ${theme}${phone ? ', phone' : ''})`, async ({ page }) => {
    test.setTimeout(180_000);
    if (phone) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: {
      theme, language, inactivityDays: 14, followUpDays: 5
    } }));
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: true, features: {} } }));
    await page.route('**/api/admin/overview', route => route.fulfill({ json: { accounts: 40, disabledAccounts: 2,
      signupsLast30Days: 5, featureWorkspaces: { opportunities: 30, offer_feed: 12 } } }));
    await page.route('**/api/admin/accounts?*', route => route.fulfill({ json: { accounts: [
      { id: 5, email: 'alice@example.com', firstName: 'Alice', lastName: 'Martin', createdAt: '2026-09-01T10:00:00Z',
        disabledAt: '2026-10-01T10:00:00Z', deleted: false, admin: false },
      { id: 1, email: 'workspace@example.test', firstName: 'Workspace', lastName: 'Test', createdAt: '2026-01-01T10:00:00Z',
        disabledAt: null, deleted: false, admin: true }], total: 2, page: 0, size: 20 } }));
    await page.route('**/api/admin/features', route => route.fulfill({ json: [
      { flag: 'OFFER_FEED', enabled: true }, { flag: 'DATA_IMPORT', enabled: false }] }));

    for (const path of PAGES) {
      await page.goto(path);
      await settled(page);
      expect(await violations(page), path).toEqual([]);
    }

    for (const tab of language === 'en' ? ['Accounts', 'Features'] : ['Comptes', 'Fonctionnalités']) {
      await page.goto('/admin');
      await page.getByRole('tab', { name: tab }).click();
      await settled(page);
      await expect(page.locator('[data-account], [data-feature]').first()).toBeVisible();
      expect(await violations(page), `admin ${tab}`).toEqual([]);
    }

    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    await expect(page.locator('app-kanban-board .kanban-column').first()).toBeVisible();
    expect(await violations(page), 'kanban board').toEqual([]);

    await page.getByRole('button', { name: language === 'en' ? 'Search everything' : 'Tout rechercher' }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    // Measured once it has faded in: halfway, its text is half transparent.
    await page.locator('.mat-mdc-dialog-container').evaluate(dialog =>
      Promise.all(dialog.getAnimations({ subtree: true }).map(animation => animation.finished)));
    expect(await violations(page), 'search dialog').toEqual([]);
  });
}

test('meets WCAG 2.1 AA on the sign-in page', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('button', { name: /Sign in|Se connecter/ }).first()).toBeVisible();
  expect(await violations(page)).toEqual([]);
});
