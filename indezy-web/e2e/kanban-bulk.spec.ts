import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

test('moves, sources and archives several Kanban cards at once', async ({ page }) => {
  test.setTimeout(60_000);
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.route('**/api/sources/by-freelance/1', route => route.fulfill({ json: [
    { id: 3, name: 'Malt', type: 'JOB_BOARD' }, { id: 2, name: 'LinkedIn', type: 'SOCIAL_MEDIA' }
  ] }));
  const card = (projectId: number, role: string) => ({
    projectId, role, status: 'CONTACT', clientName: 'Acme', totalSteps: 0, completedSteps: 0, failedSteps: 0
  });
  let cards = [card(41, 'Java developer'), card(42, 'Data engineer'), card(43, 'Cloud architect')];
  await page.route('**/api/projects/kanban/1*', route => route.fulfill({ json: {
    inactivityDays: 14, columnOrder: ['CONTACT', 'OFFER', 'LOST'],
    columns: {
      CONTACT: cards.filter(c => c.status === 'CONTACT'), OFFER: cards.filter(c => c.status === 'OFFER'),
      LOST: cards.filter(c => c.status === 'LOST')
    }
  } }));
  const changes: { projectIds: number[]; action: string; status?: string; lostReason?: string; sourceId?: number }[] = [];
  await page.route('**/api/projects/bulk', async route => {
    const change = route.request().postDataJSON();
    changes.push(change);
    if (change.action === 'STATUS') {
      cards = cards.map(c => change.projectIds.includes(c.projectId) ? { ...c, status: change.status } : c);
    } else if (change.action === 'ARCHIVE') {
      cards = cards.filter(c => !change.projectIds.includes(c.projectId));
    }
    await route.fulfill({ json: { updated: change.projectIds.length } });
  });

  await page.goto('/dashboard');
  await page.getByRole('radio', { name: /Kanban/ }).click();
  await page.getByRole('checkbox', { name: 'Select "Java developer"' }).check();
  await page.getByRole('checkbox', { name: 'Select "Data engineer"' }).check();
  await expect(page).toHaveURL(/\/dashboard$/);
  const bar = page.getByRole('toolbar', { name: '2 selected' });
  await expect(bar).toBeVisible();

  await bar.getByRole('button', { name: 'Source' }).click();
  await page.getByRole('menuitem', { name: 'Malt' }).click();
  await expect(page.getByText('2 opportunities updated')).toBeVisible();
  expect(changes[0]).toEqual({ projectIds: [41, 42], action: 'SOURCE', sourceId: 3 });
  await expect(bar).toBeHidden();

  await page.getByRole('checkbox', { name: 'Select "Data engineer"' }).check();
  await page.getByRole('checkbox', { name: 'Select "Cloud architect"' }).check();
  await page.getByRole('toolbar').getByRole('button', { name: 'Move to' }).click();
  await page.getByRole('menuitem', { name: 'Offer' }).click();
  await expect.poll(() => changes.length).toBe(2);
  expect(changes[1]).toEqual({ projectIds: [42, 43], action: 'STATUS', status: 'OFFER' });

  await page.getByRole('checkbox', { name: 'Select "Java developer"' }).check();
  await page.getByRole('toolbar').getByRole('button', { name: 'Archive' }).click();
  await expect.poll(() => changes.length).toBe(3);
  expect(changes[2]).toEqual({ projectIds: [41], action: 'ARCHIVE' });
  await expect(page.getByText('Java developer')).toBeHidden();
  await page.locator('.kanban-board').screenshot({ path: test.info().outputPath('kanban-bulk.png') });
});
