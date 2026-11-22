import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { offer } from './helpers/offer-feed';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`marks the offers of a publisher worked with or under a non-solicitation clause (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route(/\/api\/offers(\?.*)?$/, route => route.fulfill({ json: { content: [
      offer(1, { company: 'Conseil X', relation: { workedWith: true } }),
      offer(2, { company: 'Airbus', relation: { workedWith: true, clauseEsn: 'Conseil X', clauseUntil: '2027-08-31' } })
    ], page: 0, size: 20, totalElements: 2, totalPages: 1, distanceAvailable: false, matchAvailable: false } }));

    await page.goto('/offers');
    await expect(page.locator('[data-offer="1"] [data-offer-worked-with]')).toContainText(en ? 'Worked with' : 'Déjà travaillé avec');
    const clause = page.locator('[data-offer="2"] [data-offer-clause]');
    await expect(clause).toHaveAttribute('aria-label', /Conseil X/);
    const audit = await new AxeBuilder({ page }).include('[data-offer="2"]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
  });
}
