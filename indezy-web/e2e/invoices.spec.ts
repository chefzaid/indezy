import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const base = { status: 'DRAFT', overdue: false, clientId: 3, clientName: 'Acme', projectId: null, projectRole: null,
  activityReportId: null, issueDate: null, dueDate: null, periodStart: null, periodEnd: null, language: 'FR', notes: null,
  lines: [], vat: 0, paidOn: null, createdAt: '2026-10-27T10:00:00' };

for (const width of [1280, 390]) {
  test(`writes, issues and settles an invoice (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let invoices: Record<string, unknown>[] = [{ ...base, id: 1, number: 'F2026-0001', status: 'ISSUED', overdue: true, issueDate: '2026-09-01', dueDate: '2026-10-01',
      totalExcludingVat: 600, total: 600 }];
    const calls: unknown[] = [];
    await page.route('**/api/users/billing', route => route.fulfill({ json: { vatExempt: true, vatRate: 20, complete: true } }));
    await page.route('**/api/invoices/by-freelance/1', route => route.fulfill({ json: invoices }));
    await page.route('**/api/invoices', route => {
      calls.push(route.request().postDataJSON());
      const created = { ...base, id: 2, number: null, ...route.request().postDataJSON(), totalExcludingVat: 1300, total: 1300 };
      invoices = [created, ...invoices];
      return route.fulfill({ status: 201, json: created });
    });
    await page.route('**/api/invoices/2/issue', route => {
      calls.push('issue');
      return route.fulfill({ json: { ...invoices[0], status: 'ISSUED', number: 'F2026-0002', dueDate: '2026-11-26' } });
    });
    await page.route('**/api/invoices/1/payment', route => {
      calls.push(route.request().postDataJSON());
      return route.fulfill({ json: { ...invoices.at(-1), status: 'PAID', overdue: false, paidOn: route.request().postDataJSON().paidOn } });
    });

    await page.goto('/invoices');
    await expect(page.locator('[data-invoice="1"]')).toContainText('Overdue');
    await expect(page.locator('[data-invoice-figures]')).toContainText('600.00');

    await page.locator('[data-new-invoice]').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('combobox', { name: 'Client' }).click();
    await page.getByRole('option', { name: 'Acme' }).click();
    await dialog.getByLabel('Description').fill('Audit');
    await dialog.getByLabel('Quantity').fill('2');
    await dialog.getByLabel('Unit price').fill('650');
    await expect(dialog.locator('[data-draft-totals]')).toContainText('1,300.00');
    await page.locator('.mat-mdc-dialog-container').evaluate(container =>
      Promise.all(container.getAnimations({ subtree: true }).map(animation => animation.finished)));
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-save-draft]').click();
    await expect(page.locator('[data-invoice="2"]')).toContainText('Draft');

    await page.locator('[data-invoice="2"]').getByRole('button', { name: /Actions for/ }).click();
    await page.getByRole('menuitem', { name: 'Issue' }).click();
    await page.locator('[data-invoice-date-confirm]').click();
    await expect(page.locator('[data-invoice="2"]')).toContainText('F2026-0002');

    await page.locator('[data-invoice="1"]').getByRole('button', { name: /Actions for/ }).click();
    await page.getByRole('menuitem', { name: 'Mark as paid' }).click();
    await page.locator('[data-invoice-date-confirm]').click();
    await expect(page.locator('[data-invoice="1"]')).toContainText('Paid');

    await page.route('**/api/invoices/1/credit-note', route => {
      calls.push('credit');
      const note = { ...base, id: 3, kind: 'CREDIT_NOTE', number: null, creditedInvoiceId: 1, creditedNumber: 'F2026-0001',
        lines: [{ description: 'Audit', quantity: 1, unitPrice: 600, vatRate: 0 }], totalExcludingVat: 600, total: 600 };
      invoices = [note, ...invoices];
      return route.fulfill({ status: 201, json: note });
    });
    await page.route('**/api/invoices/3', route => {
      calls.push(route.request().postDataJSON());
      return route.fulfill({ json: { ...invoices[0], ...route.request().postDataJSON(), totalExcludingVat: 200, total: 200 } });
    });
    await page.locator('[data-invoice="1"]').getByRole('button', { name: /Actions for/ }).click();
    await page.getByRole('menuitem', { name: 'Create a credit note' }).click();
    await expect(dialog.getByRole('heading', { name: 'Change the credit note' })).toBeVisible();
    await dialog.getByLabel('Unit price').fill('200');
    await dialog.locator('[data-save-draft]').click();
    await expect(page.locator('[data-invoice="3"]')).toContainText('Credit note');
    await expect(page.locator('[data-invoice="3"]')).toContainText('-200.00');

    await page.route('**/api/invoices/export/2026?**', route => {
      calls.push(new URL(route.request().url()).search);
      return route.fulfill({ body: 'JournalCode\tJournalLib', contentType: 'text/plain' });
    });
    await page.locator('[data-export-invoices]').click();
    await page.locator('[data-export-year="2026"]').click();
    const download = page.waitForEvent('download');
    await page.locator('[data-export-fec]').click();
    expect((await download).suggestedFilename()).toBe('FEC20261231.txt');
    expect(calls.at(-1)).toBe('?format=FEC&language=EN');

    expect(calls[0]).toEqual(expect.objectContaining({ clientId: 3, language: 'FR',
      lines: [{ description: 'Audit', quantity: 2, unitPrice: 650, vatRate: 0 }] }));
    expect(calls[1]).toBe('issue');
    expect(calls[3]).toBe('credit');
    expect(calls[4]).toEqual(expect.objectContaining({ clientId: 3,
      lines: [{ description: 'Audit', quantity: 1, unitPrice: 200, vatRate: 0 }] }));
    const pageAudit = await new AxeBuilder({ page }).include('.invoices-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(pageAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
