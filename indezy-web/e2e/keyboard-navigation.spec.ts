import { expect, Page, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

/** Cards and rows opened by a click open on Enter too, and table rows have a link to tab to. */
async function workspace(page: Page): Promise<void> {
  await page.setViewportSize({ width: 1280, height: 900 });
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const java = { id: 42, role: 'Java developer', status: 'INTERVIEW', clientId: 3, clientName: 'Acme', dailyRate: 650 };
  const won = { id: 44, role: 'Kotlin lead', status: 'WON', clientId: 3, clientName: 'Acme', dailyRate: 700, startDate: '2026-01-05' };
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [java, won] }));
  await page.route('**/api/projects/by-client/3', route => route.fulfill({ json: [java] }));
  await page.route('**/api/projects/44', route => route.fulfill({ json: won }));
  await page.route('**/api/interview-steps/by-freelance/1*', route => route.fulfill({ json: [
    { id: 7, title: 'Client interview', status: 'PLANNED', date: '2099-11-03T10:00:00', projectId: 42, projectRole: 'Java developer' }
  ] }));
}

test('opens a Kanban card with Enter, but not from a button on it', async ({ page }) => {
  await workspace(page);
  await page.goto('/dashboard');
  await page.getByRole('radio', { name: /Kanban/ }).click();
  const card = page.locator('[data-project-id="42"] .card-content');
  await card.locator('.favorite-btn').focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/dashboard$/);

  await card.focus();
  await expect(card).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/projects\/42$/);
});

test('opens an upcoming step and an archived opportunity with Enter', async ({ page }) => {
  await workspace(page);
  // The former timeline address opens the calendar's upcoming list.
  await page.goto('/timeline');
  await expect(page).toHaveURL(/\/calendar\?view=upcoming$/);
  await page.locator('[data-calendar-upcoming] a.detail-step').first().focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/projects\/42$/);

  await page.goto('/archive');
  await page.locator('.archive-card', { hasText: 'Kotlin lead' }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/projects\/44$/);
});

test('reaches a client, its opportunities and its contacts from the keyboard', async ({ page }) => {
  await workspace(page);
  await page.goto('/clients');
  const client = page.getByRole('link', { name: 'Acme' });
  await client.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/clients\/3$/);

  await page.getByRole('tab', { name: /^Projects/ }).focus();
  await page.keyboard.press('Enter');
  const opportunity = page.getByRole('link', { name: 'Java developer' });
  await opportunity.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/projects\/42$/);

  await page.goBack();
  await page.getByRole('tab', { name: /Contacts/ }).focus();
  await page.keyboard.press('Enter');
  // The org chart below the table links the contact too.
  await page.locator('table').getByRole('link', { name: 'Alice Martin' }).focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/contacts\/2$/);
});
