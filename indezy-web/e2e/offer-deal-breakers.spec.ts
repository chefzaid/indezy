import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { offer } from './helpers/offer-feed';

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`flags the deal-breakers on the feed's cards (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    await page.route(/\/api\/offers(\?.*)?$/, route => route.fulfill({ json: { content: [
      offer(1, { dealBreakers: ['ON_SITE_ONLY', 'RATE_UNDER_TARGET', 'PORTAGE_REQUIRED'] }),
      offer(2, {})
    ], page: 0, size: 20, totalElements: 2, totalPages: 1, distanceAvailable: false, matchAvailable: false } }));

    await page.goto('/offers');
    const breakers = page.locator('[data-offer="1"] [data-offer-deal-breakers]');
    await expect(breakers.locator('[data-deal-breaker]')).toHaveText(en ? ['On site every day', 'Rate under your target', 'Portage required']
      : ['Sur site tous les jours', 'TJM sous votre objectif', 'Portage obligatoire']);
    await expect(page.locator('[data-offer="2"] [data-offer-deal-breakers]')).toHaveCount(0);
    const audit = await new AxeBuilder({ page }).include('[data-offer="1"]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
