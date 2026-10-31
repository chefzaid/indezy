import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const clause = { projectId: 42, missionId: 7, missionRole: 'Lead Java', middlemanName: 'ESN Conseil', clientName: 'Acme',
  months: 12, endsOn: '2027-03-31' };

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`warns that an ESN's non-solicitation clause covers an opportunity (${language})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    await page.route('**/api/projects/non-solicitation/1', route => route.fulfill({ json: [clause] }));

    await page.goto('/projects/42');
    const notice = page.locator('[data-non-solicitation]');
    await expect(notice).toContainText('ESN Conseil');
    await expect(notice).toContainText(language === 'en' ? 'until March 31, 2027' : 'jusqu\'au 31 mars 2027');
    await expect(notice.getByRole('link', { name: 'Lead Java' })).toHaveAttribute('href', '/projects/7');
    const audit = await new AxeBuilder({ page }).include('[data-non-solicitation]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);

    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    await expect(page.locator('[data-card-non-solicitation]')).toHaveCount(1);
  });
}

test('records the clause months on a mission signed through an ESN', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const esn = { id: 5, companyName: 'ESN Conseil', city: 'Paris', isFinal: false };
  await page.route('**/api/clients/by-freelance/1', route => route.fulfill({ json: [
    { id: 3, companyName: 'Acme', city: 'Lyon', isFinal: true }, esn] }));
  let saved: Record<string, unknown> = {};
  await page.route('**/api/projects/42', async route => {
    if (route.request().method() === 'PUT') {
      saved = route.request().postDataJSON();
      return route.fulfill({ json: { ...saved, id: 42 } });
    }
    return route.fulfill({ json: { id: 42, role: 'Java developer', status: 'WON', clientId: 3, clientName: 'Acme', middlemanId: 5,
      middlemanName: 'ESN Conseil', dailyRate: 650, workMode: 'HYBRID', freelanceId: 1 } });
  });
  await page.goto('/projects/42/edit');
  const months = page.locator('[data-non-solicitation-months]');
  await months.fill('37');
  await months.blur();
  await expect(page.getByText('Enter a whole number of months from 1 to 36')).toBeVisible();
  await months.fill('12');
  await page.locator('.form-actions button[color="primary"]').click();
  await expect.poll(() => saved['nonSolicitationMonths']).toBe(12);
});
