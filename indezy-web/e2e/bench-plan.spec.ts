import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`shows this week's goal against the bench on the dashboard (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route(/\/api\/projects\/stats\/dashboard\/1/, route => route.fulfill({ json: { ...emptyDashboardStats, totalProjects: 3,
      benchPlan: { projectId: 42, role: 'Java developer', endDate: '2027-01-04', weeksLeft: 7, activeOpportunities: 2,
        advancedOpportunities: 1, addedThisWeek: 1, daysSinceLastAdded: 9, weeklyGoal: 3 } } }));

    await page.goto('/dashboard');
    const card = page.locator('[data-bench-plan]');
    await expect(card).toContainText(en ? 'Java developer ends in 7 weeks' : 'Java developer se termine dans 7 semaines');
    await expect(card).toContainText(en ? 'Add 3 opportunities this week.' : 'Ajoutez 3 opportunités cette semaine.');
    await expect(card.locator('.count')).toHaveText('1/3');
    const audit = await new AxeBuilder({ page }).include('[data-bench-plan]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
