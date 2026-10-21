import { expect, test } from '@playwright/test';
import { emptyDashboardStats } from './helpers/workspace';
import { mockFeed, offer } from './helpers/offer-feed';

test('opens an offer\'s own page from the feed and the dashboard, and shares it (en)', async ({ page, context }) => {
  test.setTimeout(60_000);
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  const { states } = await mockFeed(page, 'en');
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
  // Offer 2 expired yesterday; offer 99 is no longer collected.
  await page.route(/\/api\/offers\/2$/, route => route.fulfill({ json: offer(2, {
    title: 'Data engineer Spark', expired: true, expiresAt: new Date(Date.now() - 86_400_000).toISOString(), description: 'Plateforme data.' }) }));
  await page.route(/\/api\/offers\/99$/, route => route.fulfill({ status: 404, json: { message: 'Job offer not found with id: 99' } }));

  await page.goto('/offers');
  await page.locator('[data-offer="1"] .more-button').click();
  await page.getByRole('menuitem', { name: 'Open its page' }).click();
  await expect(page).toHaveURL(/\/offers\/1$/);
  await expect(page.getByRole('heading', { level: 1, name: 'Développeur Java 1' })).toBeAttached();
  await expect(page.getByText('Refonte complète du SI bancaire.')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Open on Free-Work' })).toHaveAttribute('href', offer(1).url as string);
  await page.getByRole('button', { name: 'Save the offer' }).click();
  await expect.poll(() => states).toContainEqual(expect.objectContaining({ saved: true }));
  await page.getByRole('button', { name: 'Copy the link' }).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('http://127.0.0.1:4200/offers/1');
  await page.locator('[data-offer-page]').screenshot({ path: test.info().outputPath('offer-page.png') });

  await page.goto('/offers/2');
  await expect(page.locator('[data-offer-expired]')).toContainText('This offer expired on');
  await page.goto('/offers/99');
  await expect(page.getByRole('heading', { name: 'This offer is no longer in the feed' })).toBeVisible();
  await page.getByRole('link', { name: 'See the feed' }).click();
  await expect(page).toHaveURL(/\/offers$/);

  await page.goto('/dashboard');
  await page.locator('app-dashboard-offers').getByRole('link', { name: 'Développeur Java 1' }).click();
  await expect(page).toHaveURL(/\/offers\/1$/);
});

test('reads an offer\'s page on a phone (fr)', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockFeed(page, 'fr');

  await page.goto('/offers/1');
  await expect(page.getByText('Refonte complète du SI bancaire.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Copier le lien' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Toutes les offres' })).toHaveAttribute('href', '/offers');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.locator('[data-offer-page]').screenshot({ path: test.info().outputPath('offer-page-phone.png') });
});
