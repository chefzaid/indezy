import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const trip = { projectId: 42, projectRole: 'Java lead', origin: '1 rue de Rivoli, Issou', destination: 'Montrouge',
  remote: false, onsiteDaysPerMonth: 20, estimated: true };

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`shows an opportunity's commute by car and by transit and what a remote day gives back (${language})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    await page.route('**/api/commute/project/42', route => route.fulfill({ json: [
      { ...trip, travelMode: 'DRIVING', durationInSeconds: 3000, rushDurationInSeconds: 6300, distanceInMeters: 56000,
        monthlySeconds: 6300 * 40 },
      { ...trip, travelMode: 'TRANSIT', durationInSeconds: 5400, rushDurationInSeconds: 6000, distanceInMeters: 56000,
        monthlySeconds: 6000 * 40 }] }));

    await page.goto('/projects/42');
    const card = page.locator('[data-project-commute]');
    await expect(card.locator('[data-commute-mode="DRIVING"]')).toContainText('≈ 50 min (56 km)');
    await expect(card.locator('[data-commute-mode="DRIVING"]')).toContainText('≈ 1 h 45');
    await expect(card.locator('[data-commute-mode="TRANSIT"]')).toContainText(language === 'en' ? '66.7 h a month' : '66,7 h par mois');
    await expect(card.locator('[data-remote-day-saving]')).toContainText(language === 'en' ? '13.3 h a month' : '13,3 h par mois');
    const audit = await new AxeBuilder({ page }).include('[data-project-commute]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('shows no commute for a remote opportunity', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.route('**/api/commute/project/42', route => route.fulfill({ json: [
    { ...trip, travelMode: 'DRIVING', remote: true, onsiteDaysPerMonth: 0, monthlySeconds: 0 },
    { ...trip, travelMode: 'TRANSIT', remote: true, onsiteDaysPerMonth: 0, monthlySeconds: 0 }] }));
  await page.goto('/projects/42');
  await expect(page.locator('[data-payment-delay], .header-card').first()).toBeVisible();
  await expect(page.locator('[data-project-commute]')).toHaveCount(0);
});

test('chips each Kanban card with its drive at rush hour', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.route('**/api/commute/projects/1*', route => route.fulfill({ json: [
    { project: { id: 42 }, commute: { ...trip, travelMode: 'DRIVING', durationInSeconds: 3000, rushDurationInSeconds: 6300 } }] }));
  await page.goto('/dashboard');
  await page.getByRole('radio', { name: /Kanban/ }).click();
  const chip = page.locator('[data-card-commute]');
  await expect(chip).toHaveCount(1);
  await expect(chip).toContainText('≈ 1 h 45');
  const audit = await new AxeBuilder({ page }).include('app-kanban-board').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});
