import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

test('saves an opportunity\'s steps as a template and applies one, its ESN\'s first', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
  await page.route('**/api/projects/42', route => route.fulfill({ json: {
    id: 42, role: 'Java developer', status: 'INTERVIEW', dailyRate: 650, clientId: 3, clientName: 'Airbus', middlemanId: 5, middlemanName: 'Alten'
  } }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  let steps = [{ id: 7, title: 'Commercial interview', status: 'VALIDATED', projectId: 42, checklist: [] }];
  await page.route('**/api/interview-steps/by-project/42/ordered', route => route.fulfill({ json: steps }));
  const templates = [
    { id: 1, name: 'Usual', steps: [{ title: 'Call', checklist: [] }] },
    { id: 2, name: 'Alten process', clientId: 5, clientName: 'Alten', steps: [
      { title: 'Technical test', checklist: ['Revise Spring'] }, { title: 'Client interview', checklist: [] }
    ] }
  ];
  const created: unknown[] = [];
  await page.route('**/api/pipeline-templates', async route => {
    if (route.request().method() === 'POST') {
      created.push(route.request().postDataJSON());
      await route.fulfill({ status: 201, json: { id: 9, name: 'Alten usual', clientId: 5, steps: [] } });
    } else {
      await route.fulfill({ json: templates });
    }
  });
  await page.route('**/api/pipeline-templates/2/apply/42', async route => {
    const added = [
      { id: 8, title: 'Technical test', status: 'TO_PLAN', projectId: 42, checklist: [{ label: 'Revise Spring', done: false }] },
      { id: 9, title: 'Client interview', status: 'TO_PLAN', projectId: 42, checklist: [] }
    ];
    steps = [...steps, ...added];
    await route.fulfill({ json: added });
  });

  await page.goto('/projects/42');
  await page.getByRole('button', { name: 'Templates' }).click();
  const items = page.getByRole('menuitem');
  await expect(items.first()).toContainText('Alten process');
  await expect(items.first()).toContainText('For Alten · 2 step(s)');
  await expect(items.nth(1)).toContainText('Any client · 1 step(s)');
  await page.getByRole('menuitem', { name: 'Save these steps as a template' }).click();

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Template name')).toHaveValue('Alten');
  await dialog.getByLabel('Template name').fill('Alten usual');
  await dialog.getByRole('button', { name: 'Save' }).click();
  await expect(dialog).toBeHidden();
  expect(created).toEqual([{ name: 'Alten usual', projectId: 42, clientId: 5 }]);

  await page.getByRole('button', { name: 'Templates' }).click();
  await page.getByRole('menuitem', { name: /Alten process/ }).click();
  await expect(page.getByText('2 step(s) added from "Alten process"')).toBeVisible();
  await expect(page.getByText('Technical test')).toBeVisible();
  await expect(page.getByText('Preparation · 0/1 ready')).toBeVisible();
});
