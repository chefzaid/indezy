import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const usage = { dayCeiling: 4, amountCeiling: null, periodEnd: null, workedDays: 3.5, amount: 2100, share: 0.875, daysLeft: 0.5,
  level: 'NEAR' };
const order = { id: 5, projectId: 42, number: 'BC-2026-118', periodStart: '2026-11-01', periodEnd: null, dayCeiling: 4,
  amountCeiling: null, notes: null, amendments: [], usage };

for (const width of [1280, 390]) {
  test(`follows a mission's purchase order and its amendment (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    await page.route('**/api/projects/42', route => route.fulfill({ json: {
      id: 42, role: 'Java developer', status: 'WON', clientName: 'Société Générale', dailyRate: 600, workMode: 'HYBRID',
      startDate: '2026-11-01', durationInMonths: 3
    } }));
    const calls: unknown[] = [];
    await page.route('**/api/projects/42/purchase-orders', route => {
      if (route.request().method() === 'GET') {
        return route.fulfill({ json: [] });
      }
      const body = route.request().postDataJSON();
      calls.push(body);
      return route.fulfill({ status: 201, json: body.amendsId ? { ...order, amendments: [{ id: 6, number: body.number,
        periodEnd: body.periodEnd, dayCeiling: body.dayCeiling, amountCeiling: null, notes: null }],
        usage: { ...usage, dayCeiling: 14, periodEnd: body.periodEnd, share: 0.25, daysLeft: 10.5, level: 'OK' } } : order });
    });

    await page.goto('/projects/42');
    const card = page.locator('app-purchase-orders-card');
    await expect(card).toContainText('No purchase order yet');
    await card.locator('[data-add-order]').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Order number').fill('BC-2026-118');
    await dialog.getByLabel('Starts on').fill('2026-11-01');
    await dialog.locator('[data-save-order]').click();
    await expect(dialog.locator('[data-order-error]')).toHaveText('Give a ceiling in days or in euros.');
    await dialog.getByLabel('Ceiling in days').fill('4');
    await page.locator('.mat-mdc-dialog-container').evaluate(container =>
      Promise.all(container.getAnimations({ subtree: true }).map(animation => animation.finished)));
    const dialogAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(dialogAudit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-save-order]').click();

    const saved = card.locator('[data-order="5"]');
    await expect(saved).toContainText('3.5 of 4 days used');
    await expect(saved).toContainText('0.5 days left');
    await expect(saved.locator('[data-order-alert]')).toContainText('time to ask for an amendment');
    const cardAudit = await new AxeBuilder({ page }).include('app-purchase-orders-card').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(cardAudit.violations.map(violation => violation.id)).toEqual([]);

    await saved.getByRole('button', { name: 'Actions for BC-2026-118' }).click();
    await page.getByRole('menuitem', { name: 'Add an amendment' }).click();
    await dialog.getByLabel('Amendment number').fill('Avenant 1');
    await dialog.getByLabel('Days added').fill('10');
    await dialog.getByLabel('New end').fill('2027-03-31');
    await dialog.locator('[data-save-order]').click();
    await expect(saved.locator('[data-amendment="6"]')).toContainText('Avenant 1 · +10 days · until Mar 31, 2027');
    await expect(saved.locator('[data-order-alert]')).toHaveCount(0);

    expect(calls).toEqual([
      { amendsId: null, number: 'BC-2026-118', periodStart: '2026-11-01', periodEnd: null, dayCeiling: 4, amountCeiling: null, notes: null },
      { amendsId: 5, number: 'Avenant 1', periodStart: null, periodEnd: '2027-03-31', dayCeiling: 10, amountCeiling: null, notes: null }
    ]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
