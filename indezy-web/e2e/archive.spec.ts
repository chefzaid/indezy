import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

test('archives an opportunity from its page and restores it from the archive', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
  await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  let project: Record<string, unknown> = {
    id: 42, role: 'Java developer', status: 'OFFER', clientName: 'Acme', dailyRate: 650, startDate: '2026-11-02'
  };
  await page.route('**/api/projects/42', route => route.fulfill({ json: project }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [project] }));
  await page.route('**/api/projects/42/archive', async route => {
    project = { ...project, archivedAt: '2026-10-26T09:15:00' };
    await route.fulfill({ json: project });
  });
  await page.route('**/api/projects/42/restore', async route => {
    project = { ...project, archivedAt: null };
    await route.fulfill({ json: project });
  });

  await page.goto('/projects/42');
  await page.getByRole('button', { name: 'Archive', exact: true }).click();
  await expect(page.getByText('Archived Oct 26, 2026')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Restore' })).toBeVisible();

  await page.goto('/archive');
  await expect(page.getByText('Java developer')).toBeVisible();
  await page.getByRole('button', { name: 'Restore' }).click();
  await expect(page.getByText('Opportunity back in its pipeline column')).toBeVisible();
  await expect(page.getByText('Java developer')).toBeHidden();
});

test('turns on the nightly archiving of idle opportunities', async ({ page }) => {
  await mockWorkspaceSession(page, 'fr');
  const saved: Record<string, unknown>[] = [];
  await page.route('**/api/users/preferences', async route => {
    if (route.request().method() === 'PUT') {
      saved.push(route.request().postDataJSON());
      await route.fulfill({ json: saved.at(-1) });
    } else {
      await route.fulfill({ json: { theme: 'light', language: 'fr', inactivityDays: 21, autoArchive: false } });
    }
  });
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Ada', lastName: 'L', email: 'a@b.fr', skills: [] } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
  await page.route('**/api/users/security', route => route.fulfill({ json: {} }));

  await page.goto('/profile');
  await page.getByRole('tab', { name: /Préférences/ }).click();
  await page.getByRole('switch', { name: 'Archiver chaque nuit les opportunités inactives' }).click();
  await page.getByRole('tabpanel').getByRole('button', { name: 'Sauvegarder' }).click();

  await expect.poll(() => saved.length).toBe(1);
  expect(saved[0]).toEqual(expect.objectContaining({ autoArchive: true, inactivityDays: 21 }));
});
