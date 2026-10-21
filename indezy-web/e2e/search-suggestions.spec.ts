import { expect, Page, test } from '@playwright/test';
import { isSearch, mockFeed } from './helpers/offer-feed';

const suggestions = {
  suggestions: [
    { key: 'java-lyon', filters: { skills: ['Java'], rateMin: 600, radiusKm: 30, regions: [], workModes: [], view: 'FEED', sort: 'NEWEST', page: 0 },
      place: 'Lyon', offers: 12, goodMatches: 3, newThisWeek: 1 },
    { key: 'java-remote', filters: { skills: ['Java'], rateMin: 600, workModes: ['REMOTE'], view: 'FEED', sort: 'NEWEST', page: 0 },
      offers: 5, goodMatches: 2, newThisWeek: 0 },
    { key: 'kafka', filters: { skills: ['Kafka'], rateMin: 600, view: 'FEED', sort: 'NEWEST', page: 0 }, offers: 2, goodMatches: 0, newThisWeek: 2 }
  ],
  missing: ['POSITION']
};

/** The feed with no saved search yet: what is saved and dismissed is recorded. */
async function mockSuggestions(page: Page, savedSearches: unknown[]): Promise<{ saves: Record<string, unknown>[]; dismissed: unknown[] }> {
  const saves: Record<string, unknown>[] = [];
  const dismissed: unknown[] = [];
  await page.route('**/api/offers/searches/suggestions', route => route.fulfill({ json: suggestions }));
  await page.route('**/api/offers/searches/suggestions/dismissals', route => {
    dismissed.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  await page.route(/\/api\/offers\/searches$/, route => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: savedSearches });
    }
    const body = route.request().postDataJSON();
    saves.push(body);
    return route.fulfill({ status: 201, json: { id: 7, name: body.name, filters: body.filters, lastOpenedAt: new Date().toISOString(), newOffers: 0 } });
  });
  return { saves, dismissed };
}

test('suggests searches from the profile to preview, save or dismiss (en)', async ({ page }) => {
  test.setTimeout(60_000);
  const { searches } = await mockFeed(page, 'en');
  const { saves, dismissed } = await mockSuggestions(page, []);

  await page.goto('/offers');
  const panel = page.locator('[data-search-suggestions]');
  await expect(panel.getByRole('heading', { name: 'Suggested for you' })).toBeVisible();
  await expect(panel.locator('.suggestion-name')).toHaveText(['Java · 600 €/day or more · Lyon + 30 km', 'Java · 600 €/day or more · Full remote',
    'Kafka · 600 €/day or more']);
  await expect(panel.locator('.suggestion-counts').first()).toHaveText('12 open offers · 3 match your profile · 1 new this week');
  await expect(panel.locator('[data-missing-profile]')).toContainText('fill in on your profile: job title.');
  await panel.screenshot({ path: test.info().outputPath('search-suggestions.png') });

  const previewed = page.waitForRequest(request => isSearch(request) && new URL(request.url()).searchParams.get('radiusKm') === '30');
  await panel.getByRole('button', { name: 'Preview the offers of Java · 600 €/day or more · Lyon + 30 km' }).click();
  const preview = new URL((await previewed).url());
  expect(preview.searchParams.getAll('skills')).toEqual(['Java']);
  expect(preview.searchParams.get('rateMin')).toBe('600');
  await expect(page).toHaveURL(/skills=Java/);

  await panel.getByRole('button', { name: 'Save the search Java · 600 €/day or more · Full remote' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('textbox')).toHaveValue('Java · 600 €/day or more · Full remote');
  await dialog.getByRole('textbox').fill('Java remote');
  await dialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.locator('[data-saved-search] .search-name')).toHaveText(['Java remote']);
  expect(saves[0]).toEqual(expect.objectContaining({ name: 'Java remote',
    filters: expect.objectContaining({ skills: ['Java'], workModes: ['REMOTE'], rateMin: 600 }) }));
  await expect(panel.locator('.suggestion-name')).toHaveCount(2);

  await panel.getByRole('button', { name: 'Dismiss the suggestion Kafka · 600 €/day or more' }).click();
  await expect(panel.locator('.suggestion-name')).toHaveText(['Java · 600 €/day or more · Lyon + 30 km']);
  expect(dismissed).toEqual([expect.objectContaining({ skills: ['Kafka'] })]);

  // Closed, they stay closed on the next visit.
  await page.getByRole('button', { name: 'Suggested searches' }).click();
  await expect(panel).toHaveCount(0);
  await page.reload();
  await expect(page.locator('[data-saved-search]')).toHaveCount(0);
  await expect(panel).toHaveCount(0);
  expect(searches.length).toBeGreaterThan(0);
});

test('offers the suggested searches once the profile changes (fr)', async ({ page }) => {
  test.setTimeout(60_000);
  await page.setViewportSize({ width: 390, height: 844 });
  await mockFeed(page, 'fr');
  await mockSuggestions(page, [{ id: 5, name: 'Java remote', filters: { skills: ['Java'] }, lastOpenedAt: new Date().toISOString(), newOffers: 0 }]);
  let profile: Record<string, unknown> = { id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test', skills: ['Java'],
    targetDailyRate: 550 };
  await page.route('**/api/users/profile', async route => {
    if (route.request().method() === 'PUT') {
      profile = { ...profile, ...route.request().postDataJSON() };
    }
    await route.fulfill({ json: profile });
  });
  await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));

  await page.goto('/profile');
  await page.getByRole('spinbutton', { name: 'TJM cible', exact: true }).fill('600');
  await page.getByRole('tabpanel').getByRole('button', { name: 'Sauvegarder', exact: true }).click();
  await page.getByRole('button', { name: 'Les voir' }).click();

  await expect(page).toHaveURL(/\/offers/);
  const panel = page.locator('[data-search-suggestions]');
  await expect(panel.locator('.suggestion-name').first()).toHaveText('Java · 600 €/jour ou plus · Lyon + 30 km');
  await expect(page.locator('[data-saved-search] .search-name')).toHaveText(['Java remote']);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await panel.screenshot({ path: test.info().outputPath('search-suggestions-phone.png') });
});
