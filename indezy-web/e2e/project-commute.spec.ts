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
        monthlySeconds: 6300 * 40, beyondMaximum: true, dailyCost: 33.6 },
      { ...trip, travelMode: 'TRANSIT', durationInSeconds: 5400, rushDurationInSeconds: 6000, distanceInMeters: 56000,
        monthlySeconds: 6000 * 40 }] }));

    await page.goto('/projects/42');
    const card = page.locator('[data-project-commute]');
    await expect(card.locator('[data-commute-mode="DRIVING"]')).toContainText('≈ 50 min (56 km)');
    await expect(card.locator('[data-commute-mode="DRIVING"]')).toContainText('≈ 1 h 45');
    await expect(card.locator('[data-commute-mode="DRIVING"] [data-commute-beyond]')).toBeVisible();
    await expect(card.locator('[data-commute-mode="TRANSIT"] [data-commute-beyond]')).toHaveCount(0);
    await expect(card.locator('[data-commute-mode="TRANSIT"]')).toContainText(language === 'en' ? '66.7 h a month' : '66,7 h par mois');
    // 650 € a day, 1 h 45 each way at 92.86 €/h and 33.60 € a day by car; 1 h 40 each way by transit, no cost set.
    await expect(card.locator('[data-commute-mode="DRIVING"] [data-effective-rate]')).toContainText(language === 'en' ? '€291' : '291 €');
    await expect(card.locator('[data-commute-mode="TRANSIT"] [data-effective-rate]')).toContainText(language === 'en' ? '€340' : '340 €');
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
    { project: { id: 42 }, commute: { ...trip, travelMode: 'DRIVING', durationInSeconds: 3000, rushDurationInSeconds: 6300,
      beyondMaximum: true } }] }));
  await page.goto('/dashboard');
  await page.getByRole('radio', { name: /Kanban/ }).click();
  const chip = page.locator('[data-card-commute]');
  await expect(chip).toHaveCount(1);
  await expect(chip).toContainText('≈ 1 h 45');
  await expect(chip).toHaveClass(/beyond/);
  await expect(chip).toContainText('longer than your longest commute');
  const audit = await new AxeBuilder({ page }).include('app-kanban-board').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});

test('sets the longest drive and public transport trip in the profile', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  let profile: Record<string, unknown> = { id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test' };
  await page.route('**/api/users/profile', async route => {
    if (route.request().method() === 'PUT') {
      profile = { ...profile, ...route.request().postDataJSON() };
    }
    await route.fulfill({ json: profile });
  });
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en', inactivityDays: 14 } }));
  await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
  await page.goto('/profile');
  const save = page.getByRole('tabpanel').getByRole('button', { name: 'Save', exact: true });
  await page.locator('[data-max-drive]').fill('601');
  await expect(save).toBeDisabled();
  await page.locator('[data-max-drive]').fill('45');
  await page.locator('[data-max-transit]').fill('60');
  await page.locator('[data-km-rate]').fill('0.601');
  await expect(save).toBeDisabled();
  await page.locator('[data-km-rate]').fill('0.6');
  await page.locator('[data-transit-pass]').fill('88.8');
  await save.click();
  await expect.poll(() => [profile['maxDriveMinutes'], profile['maxTransitMinutes'], profile['kmRate'], profile['transitPassMonthly']])
    .toEqual([45, 60, 0.6, 88.8]);
});

test('sums the pipeline commute up on the dashboard', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const result = (id: number, status: string, dailyRate: number, hours: number, onsite: number) => ({
    project: { id, status, dailyRate, role: 'Role', freelanceId: 1 },
    commute: { ...trip, projectId: id, travelMode: 'DRIVING', onsiteDaysPerMonth: onsite, remote: onsite === 0,
      durationInSeconds: 1800, monthlySeconds: hours * 3600 } });
  await page.route('**/api/commute/projects/1*', route => route.fulfill({ json: [
    result(42, 'INTERVIEW', 600, 10, 20), result(43, 'OFFER', 700, 30, 10), result(44, 'WON', 650, 12, 10)] }));
  await page.goto('/dashboard');
  const card = page.locator('[data-commute-stats]');
  await expect(card.locator('[data-commute-pipeline]')).toHaveText('20 h');
  await expect(card.locator('[data-commute-won]')).toHaveText('12 h');
  await expect(card.locator('[data-commute-remote]')).toHaveText('33 %');
  await expect(card.locator('[data-commute-rates]')).toContainText('€700 a day on average, against €625');
  const audit = await new AxeBuilder({ page }).include('[data-commute-stats]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});

test('records where a mission is worked and shows it on its page', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  let mission: Record<string, unknown> = { id: 42, role: 'Java developer', status: 'INTERVIEW', clientId: 3, clientName: 'Acme',
    dailyRate: 650, workMode: 'HYBRID', freelanceId: 1 };
  await page.route('**/api/projects/42', async route => {
    if (route.request().method() === 'PUT') {
      mission = { ...mission, ...route.request().postDataJSON() };
    }
    return route.fulfill({ json: mission });
  });
  await page.goto('/projects/42/edit');
  await page.locator('[data-work-address]').fill('Tour First, La Défense');
  await page.locator('.form-actions button[color="primary"]').click();
  await expect.poll(() => mission['workAddress']).toBe('Tour First, La Défense');
  await expect(page.locator('.info-item[data-work-address]')).toContainText('Tour First, La Défense');
});
