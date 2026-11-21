import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

const year = new Date().getFullYear();
const months = (march: number) => Array.from({ length: 12 }, (_, index) => index === 2 ? march : 0);

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`records an expense and says what it costs under the status (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    let expenses: Record<string, unknown>[] = [{ id: 1, spentOn: `${year}-03-02`, label: 'Laptop', category: 'EQUIPMENT', amount: 1200,
      vat: 200, projectId: null, projectRole: null, notes: null }];
    const created: unknown[] = [];
    const summary = () => {
      const total = expenses.reduce((sum, expense) => sum + (expense['amount'] as number), 0);
      const vat = expenses.reduce((sum, expense) => sum + (expense['vat'] as number), 0);
      return { year, legalForm: 'SASU', treatment: 'DEDUCTIBLE', total, vat, deductible: total - vat, netCost: (total - vat) * 0.6,
        reversionRate: 60, months: months(total), byCategory: Object.fromEntries(expenses.map(expense => [expense['category'], expense['amount']])) };
    };
    await page.route(/\/api\/expenses\/summary\/\d+$/, route => route.fulfill({ json: summary() }));
    await page.route(/\/api\/expenses(\?year=\d+)?$/, route => {
      if (route.request().method() === 'GET') {
        return route.fulfill({ json: expenses });
      }
      const body = route.request().postDataJSON();
      created.push(body);
      const expense = { id: 2, ...body, vat: body.vat ?? 0, projectRole: null };
      expenses = [expense, ...expenses];
      return route.fulfill({ status: 201, json: expense });
    });

    await page.goto('/expenses');
    await expect(page.locator('[data-expense="1"]')).toContainText(en ? 'Laptop' : 'Laptop');
    await expect(page.locator('[data-expense-net-cost]')).toContainText(en ? '600.00' : '600,00');
    await expect(page.locator('[data-expense-treatment="DEDUCTIBLE"]')).toContainText(en ? 'lower your taxable profit' : 'réduisent votre bénéfice');

    await page.locator('[data-new-expense]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: en ? 'New expense' : 'Nouvelle dépense' })).toBeVisible();
    await dialog.locator('input[type="date"]').fill(`${year}-03-12`);
    await dialog.locator('[data-expense-label]').fill(en ? 'Train to Lyon' : 'Train pour Lyon');
    await dialog.locator('[data-expense-amount]').fill('120');
    await dialog.locator('[data-expense-vat]').fill('200');
    await expect(dialog.locator('[data-vat-too-high]')).toBeVisible();
    await dialog.locator('[data-expense-vat]').fill('20');
    await expect(dialog.locator('[data-vat-too-high]')).toHaveCount(0);
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-save-expense]').click();

    await expect(page.locator('[data-expense="2"]')).toContainText(en ? 'Travel' : 'Déplacements');
    await expect(page.locator('[data-expense-total]')).toContainText(en ? '1,320.00' : '1 320,00');
    expect(created).toEqual([{ spentOn: `${year}-03-12`, label: en ? 'Train to Lyon' : 'Train pour Lyon', category: 'TRAVEL', amount: 120, vat: 20,
      projectId: null, notes: null }]);
    await expect(dialog).toHaveCount(0);
    const pageAudit = await new AxeBuilder({ page }).include('.expenses-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(pageAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
