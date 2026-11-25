import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

test('labels the paginators in the language of the app (fr)', async ({ page }) => {
  await mockWorkspaceSession(page, 'fr');
  await mockWorkspaceData(page, 'fr');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: true, features: {} } }));
  await page.route('**/api/admin/overview', route => route.fulfill({ json: { accounts: 40, disabledAccounts: 0, signupsLast30Days: 5,
    featureWorkspaces: {} } }));
  await page.route('**/api/admin/accounts?*', route => route.fulfill({ json: { accounts: [{ id: 5, email: 'alice@example.com',
    firstName: 'Alice', lastName: 'Martin', createdAt: '2026-09-01T10:00:00Z', disabledAt: null, deleted: false, admin: false }],
  total: 40, page: 0, size: 20 } }));
  await page.goto('/admin');
  await page.getByRole('tab', { name: 'Comptes' }).click();
  await expect(page.locator('.mat-mdc-paginator-range-label').first()).toHaveText('1 – 20 sur 40');
});
