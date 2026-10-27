import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

test('says when the connection is lost, until it is back', async ({ page, context }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.goto('/dashboard');
  await expect(page.locator('[data-offline-banner]')).toHaveCount(0);

  await context.setOffline(true);
  await expect(page.locator('[data-offline-banner]')).toContainText('You are offline');
  await context.setOffline(false);
  await expect(page.locator('[data-offline-banner]')).toHaveCount(0);
});
