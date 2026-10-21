import { expect, test } from '@playwright/test';
import { mockFeed } from './helpers/offer-feed';

test('opens what a publisher puts in the feed from an offer and hides its offers (en)', async ({ page }) => {
  await mockFeed(page, 'en');
  const blocked: unknown[] = [];
  await page.route('**/api/offers/publishers/**', route => route.fulfill({ json: {
    key: 'exemple conseil', name: 'Exemple Conseil', openMissions: 4, medianRate: 600, medianOpenDays: 12, missions: 6, republished: 2,
    sites: [{ site: 'FREE_WORK', siteName: 'Free-Work', offers: 3 }],
    offers: [{ id: 1, title: 'Développeur Java 1', siteName: 'Free-Work', rateMin: 550, rateMax: 650, publishedAt: new Date().toISOString() }]
  } }));
  await page.route('**/api/offers/blocked-publishers', route => {
    blocked.push(route.request().postDataJSON());
    return route.fulfill({ status: 201, json: { id: 9, name: 'Exemple Conseil' } });
  });

  await page.goto('/offers');
  await page.locator('[data-offer="1"]').getByRole('link', { name: 'What Exemple Conseil puts in the feed' }).click();
  await expect(page).toHaveURL(/\/offers\/publishers\/Exemple%20Conseil$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Exemple Conseil' })).toBeVisible();
  await expect(page.locator('[data-publisher-kpi="republished"] dd')).toHaveText('2 of 6 missions');
  await expect(page.locator('[data-publisher-no-client]')).toHaveText('Not in your clients');
  await page.getByRole('button', { name: 'Hide its offers' }).click();
  await expect(page.locator('[data-publisher-blocked]')).toBeVisible();
  expect(blocked).toEqual([{ name: 'Exemple Conseil' }]);
  await page.getByRole('link', { name: 'Développeur Java 1' }).click();
  await expect(page).toHaveURL(/\/offers\/1$/);
});
