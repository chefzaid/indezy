import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const language of ['en', 'fr']) {
  test(`reviews the rate before a renewal, from the reminder to the email (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: { ...emptyDashboardStats,
      upcomingRenewals: [{ projectId: 42, role: 'Java developer', clientName: 'Acme', renewalDate: '2026-11-30', daysUntilRenewal: 29,
        dailyRate: 650, market: { percent: -7, median: 700, offers: 40, skill: 'Java' } }] } }));

    await page.goto('/dashboard');
    const rate = page.locator('[data-renewal-rate="42"]');
    await expect(rate).toContainText(language === 'en' ? '€650 a day now · market median €700 for Java' : '650 € par jour actuellement · médiane du marché 700 € pour Java');
    const audit = await new AxeBuilder({ page }).include('[data-renewal-rate="42"]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await rate.locator('[data-renewal-email]').click();

    const dialog = page.getByRole('dialog');
    await expect(dialog.locator('input[formControlName="subject"]')).toHaveValue(language === 'en'
      ? 'Renewal of the Java developer mission: my daily rate' : 'Renouvellement de la mission Java developer : mon TJM');
  });
}
