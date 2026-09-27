import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`saves the inactivity preference and applies it on the Kanban board (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    await mockWorkspaceSession(page, language);
    let preferences = {
      theme: 'light', language, dateFormat: 'DD/MM/YYYY', timeFormat: '24h', currency: 'EUR',
      timezone: 'Europe/Paris', defaultView: 'dashboard', itemsPerPage: 25, autoSave: true, inactivityDays: 14
    };
    await page.route('**/api/users/preferences', async route => {
      if (route.request().method() === 'PUT') {
        preferences = { ...preferences, ...route.request().postDataJSON() };
      }
      await route.fulfill({ json: preferences });
    });
    await page.route('**/api/users/profile', route => route.fulfill({ json: {
      id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test'
    } }));
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
    await page.route('**/api/projects/kanban/1*', route => route.fulfill({ json: {
      inactivityDays: preferences.inactivityDays,
      columnOrder: ['CONTACT'],
      columns: { CONTACT: [{
        projectId: 42, role: 'Java developer', status: 'CONTACT', clientName: 'Acme',
        updatedAt: new Date(Date.now() - 20 * 86_400_000).toISOString(), totalSteps: 0, completedSteps: 0, failedSteps: 0
      }] }
    } }));

    const tabName = language === 'en' ? 'Preferences' : 'Préférences';
    const fieldName = language === 'en' ? 'Inactivity period (days)' : 'Délai d’inactivité (jours)';
    const saveName = language === 'en' ? 'Save' : 'Sauvegarder';
    await page.goto('/profile');
    await page.getByRole('tab', { name: tabName, exact: true }).click();
    const input = page.getByRole('spinbutton', { name: fieldName });
    const save = page.getByRole('tabpanel').getByRole('button', { name: saveName, exact: true });
    await expect(input).toHaveValue('14');
    for (const invalid of ['', '0', '366', '7.5']) {
      await input.fill(invalid);
      await expect(save).toBeDisabled();
    }
    await input.fill('30');
    await save.click();
    await expect.poll(() => preferences.inactivityDays).toBe(30);

    await page.reload();
    await page.getByRole('tab', { name: tabName, exact: true }).click();
    await expect(input).toHaveValue('30');
    await page.getByRole('tabpanel').screenshot({ path: test.info().outputPath('preferences.png') });

    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    const aging = page.locator('.card-aging');
    await expect(aging).toBeVisible();
    await expect(aging).not.toHaveClass(/stale/);

    await page.goto('/profile');
    await page.getByRole('tab', { name: tabName, exact: true }).click();
    await input.fill('7');
    await save.click();
    await expect.poll(() => preferences.inactivityDays).toBe(7);
    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    await expect(aging).toHaveClass(/stale/);
  });
}
