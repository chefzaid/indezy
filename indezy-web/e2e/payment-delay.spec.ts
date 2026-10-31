import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

test('says how a client pays, on its page and on its opportunity', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.route('**/api/invoices/payment-delays', route => route.fulfill({ json: [
    { clientId: 3, paidInvoices: 4, averageDays: 52, averageDaysLate: 12 }] }));

  await page.goto('/clients/3');
  await expect(page.locator('[data-payment-delay]')).toHaveText(/Pays in 52 days on average, 12 days late \(4 paid invoice\(s\)\)/);
  await page.goto('/projects/42');
  await expect(page.locator('[data-payment-delay]')).toContainText('12 days late');
});
