import { expect, test, Page } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const features = { OFFER_FEED: true, PUBLIC_PROFILES: true, DATA_IMPORT: true, YEARLY_REVIEW: true };
const alice = { id: 5, email: 'alice@example.com', firstName: 'Alice', lastName: 'Martin', createdAt: '2026-09-01T10:00:00Z',
  disabledAt: null, deleted: false, admin: false };

async function capabilities(page: Page, admin: boolean, turnedOff: Partial<typeof features> = {}): Promise<void> {
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin, features: { ...features, ...turnedOff } } }));
}

for (const width of [1280, 390]) {
  test(`an administrator follows usage, disables an account and turns a feature off (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let flags = Object.entries(features).map(([flag, enabled]) => ({ flag, enabled }));
    const calls: string[] = [];
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: true,
      features: Object.fromEntries(flags.map(state => [state.flag, state.enabled])) } }));
    await page.route('**/api/admin/overview', route => route.fulfill({ json: { accounts: 40, disabledAccounts: 2,
      signupsLast30Days: 5, featureWorkspaces: { opportunities: 30, offer_feed: 12 } } }));
    await page.route('**/api/admin/accounts?*', route => route.fulfill({ json: { accounts: [alice], total: 1, page: 0, size: 20 } }));
    await page.route('**/api/admin/accounts/5/disable', route => {
      calls.push('disable');
      return route.fulfill({ json: { ...alice, disabledAt: '2026-10-27T09:00:00Z' } });
    });
    await page.route('**/api/admin/features', route => route.fulfill({ json: flags }));
    await page.route('**/api/admin/features/OFFER_FEED', route => {
      calls.push(`offers ${route.request().postDataJSON().enabled}`);
      flags = flags.map(state => state.flag === 'OFFER_FEED' ? { ...state, enabled: false } : state);
      return route.fulfill({ json: flags });
    });

    await page.goto('/admin');
    await expect(page.locator('[data-admin-figures]')).toContainText('40');
    await expect(page.locator('[data-admin-adoption]')).toContainText('Offer feed');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await page.getByRole('tab', { name: 'Accounts' }).click();
    const row = page.locator('[data-account="5"]');
    await expect(row).toContainText('alice@example.com');
    await row.getByRole('button', { name: 'Disable' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Disable' }).click();
    await expect(row).toContainText('Disabled since');
    await expect(row.getByRole('button', { name: 'Enable' })).toBeVisible();

    await page.getByRole('tab', { name: 'Features' }).click();
    await page.getByRole('switch', { name: 'Offer feed' }).click();
    await expect(page.getByText('Feature turned off', { exact: true })).toBeVisible();
    expect(calls).toEqual(['disable', 'offers false']);
    if (width === 1280) {
      await expect(page.locator('mat-sidenav a[href="/offers"]')).toHaveCount(0);
      await expect(page.locator('mat-sidenav a[href="/admin"]')).toBeVisible();
    }
  });
}

test('keeps an account that is not an administrator out of the admin console', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await capabilities(page, false);

  await page.goto('/admin');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('mat-sidenav a[href="/admin"]')).toHaveCount(0);
});

test('hides a feature an administrator turned off, from the menu, its page and the profile', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await capabilities(page, false, { OFFER_FEED: false, PUBLIC_PROFILES: false, DATA_IMPORT: false });

  await page.goto('/offers');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.locator('mat-sidenav a[href="/offers"]')).toHaveCount(0);
  await expect(page.locator('mat-sidenav a[href="/review"]')).toBeVisible();

  await page.goto('/profile');
  await expect(page.getByRole('tab', { name: 'Account' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Public profile' })).toHaveCount(0);
  await page.getByRole('tab', { name: 'Account' }).click();
  await expect(page.locator('[data-import-file]')).toHaveCount(0);
});
