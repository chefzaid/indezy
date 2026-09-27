import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`copies and reopens project and Kanban links (${language})`, async ({ page, context }) => {
    test.setTimeout(60_000);
    if (language === 'fr') { await page.setViewportSize({ width: 390, height: 844 }); }
    await mockWorkspaceSession(page, language);
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
    await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1, reversionRate: 45 } }));
    await page.route('**/api/projects/42', route => route.fulfill({ json: {
      id: 42, role: 'Java developer', status: 'CONTACT', clientName: 'Acme', dailyRate: 650,
      documents: ['Job brief.pdf'], link: 'https://example.test/mission'
    } }));
    await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
    await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
    await page.route('**/api/projects/kanban/1*', route => route.fulfill({ json: {
      columnOrder: ['CONTACT'], columns: { CONTACT: [{ projectId: 42, role: 'Java developer', status: 'CONTACT' }] }
    } }));

    const copyName = language === 'en' ? 'Copy project link' : 'Copier le lien du projet';
    await page.goto('/projects?project=42');
    await expect(page).toHaveURL(/\/projects\/42$/);
    await expect(page.getByRole('heading', { name: 'Java developer' })).toBeVisible();
    await expect(page.getByText('Job brief.pdf')).toBeVisible();
    await expect(page.getByRole('link', { name: 'https://example.test/mission' })).toHaveAttribute('rel', 'noopener noreferrer');
    await page.getByRole('button', { name: copyName }).click();
    const sharedUrl = await page.evaluate(() => navigator.clipboard.readText());
    expect(sharedUrl).toBe('http://127.0.0.1:4200/projects?project=42');
    await page.goto(sharedUrl);
    await expect(page.getByRole('heading', { name: 'Java developer' })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Java developer' })).toBeVisible();

    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    await page.getByRole('button', { name: copyName }).click();
    await expect(page).toHaveURL(/\/dashboard$/);
    expect(await page.evaluate(() => navigator.clipboard.readText())).toBe(sharedUrl);
    await page.goto('/dashboard?project=42');
    await expect(page.getByRole('heading', { name: 'Java developer' })).toBeVisible();
    await page.locator('.header-card').screenshot({ path: test.info().outputPath('project-link.png') });

    await page.goto('/projects?project=bad');
    await expect(page).toHaveURL(/\/404$/);
    await page.route('**/api/projects/99', route => route.fulfill({ status: 404, json: {} }));
    await page.goto('/projects?project=99');
    await expect(page).toHaveURL(/\/projects$/);
    await expect(page.getByRole('heading', { name: 'Java developer' })).not.toBeVisible();
  });
}

test('returns to a shared project after sign-in', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('indezy-lang', 'en'));
  await page.route('**/api/auth/login', route => route.fulfill({ json: {
    token: `header.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }))}.browser-test`,
    user: { id: 1, email: 'workspace@example.test', firstName: 'Workspace', lastName: 'Test' }
  } }));
  await page.route('**/api/users/preferences', route => route.fulfill({ json: {} }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { skills: [] } }));
  await page.route('**/api/projects/42', route => route.fulfill({ json: { id: 42, role: 'Shared mission', status: 'CONTACT' } }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
  await page.goto('/projects?project=42');
  await expect(page).toHaveURL(/\/login\?returnUrl=%2Fprojects%3Fproject%3D42$/);
  await page.getByRole('textbox', { name: /Email/ }).fill('workspace@example.test');
  await page.getByLabel('Password', { exact: true }).fill('browser-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Shared mission' })).toBeVisible();
});
