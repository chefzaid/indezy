import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`sums up the record with a company on its page (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    await page.route('**/api/clients/3/scorecard', route => route.fulfill({ json: { opportunities: 4, open: 2, won: 1, offerRate: 67,
      processDays: 15, processes: 2, marginMin: 20, marginMax: 29, margins: 2, answerDays: 3 } }));

    await page.goto('/clients/3');
    const scorecard = page.locator('[data-company-scorecard]');
    await expect(scorecard.locator('[data-scorecard-opportunities]')).toHaveText(en ? '4 opportunities, 1 won, 2 open'
      : '4 opportunité(s) : 1 gagnée(s), 2 en cours');
    await expect(scorecard.locator('[data-scorecard-margin] dd')).toHaveText(en ? "20 to 29 % of the client's rate (2 missions)"
      : '20 à 29 % du tarif client (2 missions)');
    await expect(scorecard.locator('[data-scorecard-answer] dd')).toHaveText(en ? 'in 3 days usually' : 'en 3 jours d\'habitude');
    const audit = await new AxeBuilder({ page }).include('[data-company-scorecard]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
