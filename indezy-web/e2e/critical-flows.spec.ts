import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

test('creates an opportunity from the form and opens it', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const created: Record<string, unknown>[] = [];
  await page.route('**/api/projects', route => {
    if (route.request().method() !== 'POST') {
      return route.fallback();
    }
    created.push(route.request().postDataJSON());
    return route.fulfill({ status: 201, json: { ...route.request().postDataJSON(), id: 77, clientName: 'Acme' } });
  });
  await page.route('**/api/projects/77', route => route.fulfill({ json: {
    id: 77, role: 'Kotlin lead', status: 'CONTACT', clientId: 3, clientName: 'Acme', dailyRate: 720 } }));

  await page.goto('/projects/new');
  await page.locator('input[formcontrolname="role"]').fill('Kotlin lead');
  await page.locator('input[formcontrolname="dailyRate"]').fill('720');
  await page.locator('mat-select[formcontrolname="clientId"]').click();
  await page.getByRole('option', { name: /Acme/ }).click();
  await page.locator('.form-actions button[color="primary"]').click();

  await expect(page).toHaveURL(/\/projects\/77$/);
  expect(created).toEqual([expect.objectContaining({ role: 'Kotlin lead', dailyRate: 720, clientId: 3, status: 'CONTACT' })]);
  await expect(page.getByText('Kotlin lead').first()).toBeVisible();
});

test('drags a Kanban card to the next stage', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const moves: string[] = [];
  await page.route('**/api/projects/43/status*', route => {
    moves.push(new URL(route.request().url()).searchParams.get('status') ?? '');
    return route.fulfill({ json: { id: 43, role: 'Data engineer', status: 'INTERVIEW' } });
  });

  await page.goto('/dashboard');
  await page.getByRole('radio', { name: /Kanban/ }).click();
  const card = page.locator('[data-project-id="43"]');
  const target = page.locator('[data-project-id="42"]');
  await expect(card).toBeVisible();
  const from = (await card.boundingBox())!;
  const to = (await target.boundingBox())!;
  await page.mouse.move(from.x + from.width / 2, from.y + 20);
  await page.mouse.down();
  for (let step = 1; step <= 20; step++) {
    await page.mouse.move(from.x + from.width / 2 + (to.x - from.x) * step / 20, from.y + 20 + (to.y + to.height - from.y) * step / 20);
  }
  await page.mouse.up();

  await expect.poll(() => moves).toEqual(['INTERVIEW']);
});
