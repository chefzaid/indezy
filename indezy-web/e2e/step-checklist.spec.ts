import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

test('prepares an interview step from its checklist and its dialog', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
  await page.route('**/api/projects/42', route => route.fulfill({ json: {
    id: 42, role: 'Java developer', status: 'INTERVIEW', clientName: 'Acme', dailyRate: 650, totalSteps: 1, completedSteps: 0
  } }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  let step = {
    id: 7, title: 'Technical interview', status: 'PLANNED', date: '2026-11-03T10:00:00', projectId: 42,
    checklist: [{ label: 'Reread the job description', done: true }, { label: 'Test the video call', done: false }]
  };
  const saves: typeof step[] = [];
  await page.route('**/api/interview-steps/by-project/42/ordered', route => route.fulfill({ json: [step] }));
  await page.route('**/api/interview-steps/7', async route => {
    step = { ...step, ...route.request().postDataJSON() };
    saves.push(step);
    await route.fulfill({ json: step });
  });

  await page.goto('/projects/42');
  await expect(page.getByText('Preparation · 1/2 ready')).toBeVisible();
  await page.getByRole('checkbox', { name: 'Test the video call' }).check();
  await expect(page.getByText('Preparation · 2/2 ready')).toBeVisible();
  expect(saves[0].checklist).toEqual([
    { label: 'Reread the job description', done: true }, { label: 'Test the video call', done: true }
  ]);

  await page.locator('.step-item').getByRole('button', { name: 'Edit' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('checkbox', { name: 'Test the video call' })).toBeChecked();
  await dialog.getByRole('button', { name: 'Add suggested items' }).click();
  await dialog.getByLabel('Something to prepare').fill('Bring the portfolio');
  await dialog.getByLabel('Something to prepare').press('Enter');
  await dialog.getByRole('button', { name: 'Remove "Test the video call"' }).click();
  await dialog.getByRole('button', { name: 'Save' }).click();

  await expect(dialog).toBeHidden();
  expect(saves[1].checklist.map(item => item.label)).toEqual([
    'Reread the job description', 'Research the client and its products', 'Prepare two project examples matching the stack',
    'Prepare questions about the team and the delivery process', 'Decide the daily rate to defend',
    'Test the video call link, camera and microphone', 'Bring the portfolio'
  ]);
  await expect(page.getByText('Preparation · 1/7 ready')).toBeVisible();
});
