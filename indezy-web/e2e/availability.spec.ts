import { expect, test } from '@playwright/test';
import { mockFeed, offer } from './helpers/offer-feed';

const texts = {
  en: { field: 'Available from', typed: '11/16/2026', shown: '11/16/2026', save: 'Save', soon: 'Starts up to two weeks before you are available' },
  fr: { field: 'Disponible à partir du', typed: '16/11/2026', shown: '16/11/2026', save: 'Sauvegarder',
    soon: 'Démarre jusqu\'à deux semaines avant votre disponibilité' }
};

for (const language of ['en', 'fr'] as const) {
  test(`matches offers with the availability date of the profile (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    if (language === 'fr') { await page.setViewportSize({ width: 390, height: 844 }); }
    const t = texts[language];
    await mockFeed(page, language);
    let profile: Record<string, unknown> = {
      id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test', skills: ['Java'], availabilityDate: null
    };
    const saved: unknown[] = [];
    await page.route('**/api/users/profile', async route => {
      if (route.request().method() === 'PUT') {
        saved.push(route.request().postDataJSON());
        profile = { ...profile, ...route.request().postDataJSON() };
      }
      await route.fulfill({ json: profile });
    });
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
    // Registered last, so it answers the feed instead of the helper's: one mission starting two weeks early.
    await page.route(/\/api\/offers(\?.*)?$/, route => route.fulfill({ json: {
      content: [offer(1, { startDate: '2026-11-02', match: { score: 73, skills: ['Java'], rate: 'MEETS', place: 'NEAR', availability: 'SOON' } })],
      page: 0, size: 20, totalElements: 1, totalPages: 1, distanceAvailable: true, matchAvailable: true
    } }));

    await page.goto('/profile');
    const field = page.getByRole('textbox', { name: t.field });
    await expect(field).toBeEmpty();
    await field.fill(t.typed);
    await field.blur();
    await page.getByRole('tabpanel').getByRole('button', { name: t.save, exact: true }).click();
    await expect.poll(() => saved.length).toBe(1);
    expect(saved[0]).toEqual(expect.objectContaining({ availabilityDate: '2026-11-16' }));
    await page.reload();
    await expect(field).toHaveValue(t.shown);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await page.goto('/offers');
    await expect(page.locator('[data-match]').first()).toHaveAttribute('aria-label', new RegExp(t.soon));

    // Left empty, the date stops counting.
    await page.goto('/profile');
    await field.fill('');
    await page.getByRole('tabpanel').getByRole('button', { name: t.save, exact: true }).click();
    await expect.poll(() => saved.length).toBe(2);
    expect(saved[1]).toEqual(expect.objectContaining({ availabilityDate: null }));
  });
}
