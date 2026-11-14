import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`records a training, its certification and the funding asked, on the Documents page (${language})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    const posted: unknown[] = [];
    await page.route('**/api/trainings', route => {
      if (route.request().method() === 'POST') {
        const details = route.request().postDataJSON();
        posted.push(details);
        return route.fulfill({ status: 201, json: { ...details, id: 7, certificationValidity: 'VALID', fundingDue: true } });
      }
      return route.fulfill({ json: [] });
    });

    await page.goto('/documents');
    await page.locator('[data-add-training]').click();
    const dialog = page.getByRole('dialog');
    // Measured once it has faded in: halfway, its text is half transparent.
    await page.locator('.mat-mdc-dialog-container').evaluate(container =>
      Promise.all(container.getAnimations({ subtree: true }).map(animation => animation.finished)));
    await dialog.locator('[data-training-title]').fill('Certified Kubernetes Administrator');
    await dialog.locator('[data-training-start]').fill('2026-11-16');
    await dialog.locator('[data-training-end]').fill('2026-11-18');
    await dialog.locator('[data-training-cost]').fill('1800');
    await dialog.locator('[data-training-certification]').fill('CKA');
    await dialog.locator('[data-training-expires]').fill('2028-11-18');
    await dialog.locator('[data-training-fund]').click();
    await page.getByRole('option', { name: 'FIF PL' }).click();
    await dialog.locator('[data-training-deadline]').fill('2026-11-26');
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-save-training]').click();

    const card = page.locator('[data-training="7"]');
    await expect(card).toContainText('Certified Kubernetes Administrator');
    expect(posted[0]).toEqual(expect.objectContaining({ title: 'Certified Kubernetes Administrator', cost: 1800, certification: 'CKA',
      fund: 'FIF_PL', fundingStatus: 'TO_REQUEST', fundingDeadline: '2026-11-26' }));
    await expect(card.locator('[data-training-funding]')).toHaveClass(/due/);
    await expect(card.locator('[data-training-validity]')).toBeVisible();
    const pageAudit = await new AxeBuilder({ page }).include('[data-trainings]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(pageAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('points out a funding request due on the dashboard, and shows a valid certification on the public profile', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: { ...emptyDashboardStats,
    trainingFundingDue: [{ trainingId: 7, title: 'Certified Kubernetes Administrator', deadline: '2026-11-26' }] } }));
  await page.goto('/dashboard');
  const reminder = page.locator('[data-training-funding-due]');
  await expect(reminder).toContainText('Certified Kubernetes Administrator');
  await expect(reminder).toContainText('ask by Nov 26, 2026');

  await page.route('**/api/public/profiles/alice', route => route.fulfill({ json: { firstName: 'Alice', lastName: 'Martin', skills: [],
    languages: [], availableNow: true, certifications: ['CKA'] } }));
  await page.goto('/p/alice');
  await expect(page.locator('[data-certifications]')).toContainText('CKA');
  const audit = await new AxeBuilder({ page }).include('[data-certifications]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});
