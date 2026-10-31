import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`sets a yearly billed days target and follows it on the dashboard (${language})`, async ({ page }) => {
    if (language === 'fr') {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    let profile = {
      id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test', billedDaysTarget: null as number | null
    };
    await page.route('**/api/users/profile', async route => {
      if (route.request().method() === 'PUT') {
        profile = { ...profile, ...route.request().postDataJSON() };
      }
      await route.fulfill({ json: profile });
    });
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language, inactivityDays: 14 } }));
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: {
      ...emptyDashboardStats, billedDays: { year: 2026, billed: 42.5, target: profile.billedDaysTarget, projected: 154 }
    } }));

    await page.goto('/profile');
    const input = page.locator('[data-billed-days-target]');
    const save = page.getByRole('tabpanel').getByRole('button', { name: language === 'en' ? 'Save' : 'Sauvegarder', exact: true });
    for (const invalid of ['0', '367', '12.5']) {
      await input.fill(invalid);
      await expect(save).toBeDisabled();
    }
    await input.fill('200');
    await save.click();
    await expect.poll(() => profile.billedDaysTarget).toBe(200);

    await page.goto('/dashboard');
    const card = page.locator('[data-billed-days]');
    await expect(card).toContainText(language === 'en' ? 'Billed days 2026' : 'Jours facturés 2026');
    await expect(card.locator('[data-billed-days-billed]')).toContainText('/ 200 (21 %)');
    await expect(card.locator('[data-billed-days-projected]')).toHaveText('154');
    await expect(card.locator('[data-billed-days-gap]')).toContainText('46');
    const audit = await new AxeBuilder({ page }).include('[data-billed-days]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
  });
}
