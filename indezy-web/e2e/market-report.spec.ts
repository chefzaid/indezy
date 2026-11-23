import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const report = {
  quarter: '2026-Q3', from: '2026-07-01', to: '2026-09-30', offers: 120, threshold: 10, overall: { offers: 120, median: 600, low: 520, high: 680 },
  skills: [{ skill: 'Java', figure: { offers: 40, median: 620, low: 560, high: 700 }, previousMedian: 600, bySeniority: { SENIOR: 650 }, byRegion: {} }],
  regions: [{ regionCode: '11', name: 'Île-de-France', figure: { offers: 60, median: 640, low: 580, high: 700 } }],
  quarters: ['2026-Q3', '2026-Q2']
};

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`reads the quarterly market report without an account (${language}, ${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
    const asked: string[] = [];
    await page.route('**/api/public/market-report**', route => {
      asked.push(new URL(route.request().url()).searchParams.get('quarter') ?? '');
      return route.fulfill({ json: asked.length > 1 ? { ...report, quarter: '2026-Q2', skills: [], regions: [], overall: null } : report });
    });
    await page.goto('/market-report');
    await expect(page.locator('[data-market-report-skill="Java"]')).toContainText('+3 %');
    await expect(page.locator('[data-market-report-regions]')).toContainText('Île-de-France');
    const audit = await new AxeBuilder({ page }).include('[data-market-report]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.locator('[data-market-report-quarter]').click();
    await page.getByRole('option', { name: '2026-Q2' }).click();
    await expect(page.locator('[data-market-report-empty]')).toBeVisible();
    expect(asked).toEqual(['', '2026-Q2']);
  });
}
