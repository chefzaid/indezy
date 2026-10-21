import { expect, test } from '@playwright/test';
import { isSearch, mockFeed, offer } from './helpers/offer-feed';

test('warns about suspicious offers and hides them on request (en)', async ({ page }) => {
  await mockFeed(page, 'en');
  // Registered last, so it answers the feed instead of the helper's: offer 2 looks suspicious.
  await page.route(/\/api\/offers(\?.*)?$/, route => {
    const hide = new URL(route.request().url()).searchParams.get('hideSuspicious') === 'true';
    const content = [offer(1), offer(2, { title: 'Développeur Java urgent', company: undefined,
      suspicion: ['NO_PUBLISHER_NO_DESCRIPTION', 'RATE_OUTSIDE_MARKET'] })].filter(item => !hide || !item['suspicion']);
    return route.fulfill({ json: { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, distanceAvailable: false,
      matchAvailable: false } });
  });

  await page.goto('/offers');
  const badge = page.locator('[data-offer="2"] [data-suspicious]');
  await expect(badge).toHaveText(/Looks suspicious/);
  await expect(badge).toHaveAttribute('aria-label',
    'Looks suspicious. No publisher and no description · A daily rate far from the market for its skill');
  await expect(page.locator('[data-offer="1"] [data-suspicious]')).toHaveCount(0);

  const filtered = page.waitForRequest(request => isSearch(request) && new URL(request.url()).searchParams.get('hideSuspicious') === 'true');
  await page.getByRole('switch', { name: 'Hide suspicious offers' }).click();
  await filtered;
  await expect(page).toHaveURL(/hideSuspicious=true/);
  await expect(page.locator('[data-offer="2"]')).toHaveCount(0);
  await expect(page.locator('[data-offer="1"]')).toBeVisible();
});
