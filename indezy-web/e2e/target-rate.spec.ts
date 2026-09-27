import { expect, test } from '@playwright/test';
import type { Season } from '../src/app/models/season.models';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`uses the profile target for new seasons and compares accepted rates (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    if (language === 'fr') {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    let profile = {
      id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test', targetDailyRate: null as number | null
    };
    const seasons: Season[] = [];
    await page.route('**/api/users/profile', async route => {
      if (route.request().method() === 'PUT') {
        profile = { ...profile, ...route.request().postDataJSON() };
      }
      await route.fulfill({ json: profile });
    });
    await page.route('**/api/users/preferences', route => route.fulfill({ json: {
      theme: 'light', language, inactivityDays: 14
    } }));
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: seasons }));
    await page.route('**/api/seasons', async route => {
      const season = { ...route.request().postDataJSON(), id: 9, active: true };
      seasons.push(season);
      await route.fulfill({ status: 201, json: season });
    });
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: {
      ...emptyDashboardStats, targetDailyRate: profile.targetDailyRate, averageObtainedDailyRate: 625,
      totalProjects: 2, wonProjects: 2, averageDailyRate: 625
    } }));

    const targetName = language === 'en' ? 'Target daily rate' : 'TJM cible';
    const saveName = language === 'en' ? 'Save' : 'Sauvegarder';
    const comparisonName = language === 'en' ? 'Target and obtained daily rates' : 'TJM cible et obtenu';
    await page.goto('/profile');
    const input = page.getByRole('spinbutton', { name: targetName, exact: true });
    const save = page.getByRole('tabpanel').getByRole('button', { name: saveName, exact: true });
    await expect(input).toBeEmpty();
    for (const invalid of ['0', '-1', '650.5', '2147483648']) {
      await input.fill(invalid);
      await expect(save).toBeDisabled();
    }
    await input.fill('650');
    await save.click();
    await expect.poll(() => profile.targetDailyRate).toBe(650);
    await page.reload();
    await expect(input).toHaveValue('650');

    await page.goto('/dashboard');
    const comparison = page.getByRole('region', { name: comparisonName });
    await expect(comparison).toBeVisible();
    await expect(comparison.locator('dd').nth(0)).toContainText('650');
    await expect(comparison.locator('dd').nth(1)).toContainText('625');
    await comparison.screenshot({ path: test.info().outputPath('rate-target.png') });

    await page.getByRole('button', { name: language === 'en' ? /New season/ : /Nouvelle saison/ }).click();
    const dialog = page.getByRole('dialog');
    const seasonRate = dialog.getByRole('spinbutton', { name: language === 'en' ? 'Target daily rate' : 'TJM visé' });
    await expect(seasonRate).toHaveValue('650');
    await seasonRate.fill('700');
    await dialog.getByRole('button', { name: language === 'en' ? /Start the season/ : /Démarrer la saison/ }).click();
    await expect(dialog).not.toBeVisible();
    expect(seasons[0].targetDailyRate).toBe(700);

    await page.goto('/profile');
    await input.fill('');
    await save.click();
    await expect.poll(() => profile.targetDailyRate).toBeNull();
    await page.reload();
    await expect(input).toBeEmpty();
    await page.goto('/dashboard');
    await expect(page.getByRole('button', { name: language === 'en' ? /New season/ : /Nouvelle saison/ })).toBeEnabled();
    await expect(comparison).not.toBeVisible();
    expect(seasons[0].targetDailyRate).toBe(700);
  });
}
