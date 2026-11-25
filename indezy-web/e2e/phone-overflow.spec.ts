import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

/** Pages outside the main pages' audit, each shown at phone width. */
const PUBLIC = ['/login', '/register', '/forgot-password', '/help', '/legal', '/support'];
const WORKSPACE = ['/activity-reports', '/market-report', '/timeline', '/documents-pack', '/offers/sites', '/network',
  '/network/members', '/network/events', '/network/ideas', '/network/messages', '/network/notifications'];

async function overflow(page: import('@playwright/test').Page): Promise<number> {
  await page.waitForLoadState('networkidle');
  return page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
}

test.use({ viewport: { width: 390, height: 844 } });

test('no public page scrolls sideways at phone width', async ({ page }) => {
  for (const path of PUBLIC) {
    await page.goto(path);
    expect(await overflow(page), `${path} overflows`).toBeLessThanOrEqual(0);
  }
});

test('no other workspace page scrolls sideways at phone width', async ({ page }) => {
  test.setTimeout(120_000);
  await mockWorkspaceSession(page, 'fr');
  await mockWorkspaceData(page, 'fr');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  for (const path of WORKSPACE) {
    await page.goto(path);
    expect(await overflow(page), `${path} overflows`).toBeLessThanOrEqual(0);
  }
});
