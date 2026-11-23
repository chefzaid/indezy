import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

const base = { status: 'DRAFT', overdue: false, clientId: 3, clientName: 'Acme', projectId: null, projectRole: null,
  activityReportId: null, issueDate: null, dueDate: null, periodStart: null, periodEnd: null, language: 'FR', notes: null,
  lines: [], vat: 0, paidOn: null, createdAt: '2026-10-27T10:00:00' };

for (const width of [1280, 390]) {
  test(`invoices a month of an activity report: days worked times the daily rate (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let months = [{ reportId: 31, projectId: 42, projectRole: 'Java developer', clientName: 'Acme', year: 2026, month: 11,
      status: 'DRAFT', workedDays: 2.5, dailyRate: 600, amount: 1500 }];
    let invoices: Record<string, unknown>[] = [];
    await page.route('**/api/users/billing', route => route.fulfill({ json: { vatExempt: true, vatRate: 20, complete: true } }));
    await page.route('**/api/invoices/by-freelance/1', route => route.fulfill({ json: invoices }));
    await page.route('**/api/invoices/invoiceable-reports', route => route.fulfill({ json: months }));
    await page.route('**/api/invoices/from-report', route => {
      expect(route.request().postDataJSON()).toEqual({ projectId: 42, year: 2026, month: 11 });
      const created = { ...base, id: 9, projectId: 42, projectRole: 'Java developer', activityReportId: 31, periodStart: '2026-11-01',
        periodEnd: '2026-11-30', lines: [{ description: 'Prestation : Java developer — novembre 2026', quantity: 2.5, unitPrice: 600,
          vatRate: 0 }], totalExcludingVat: 1500, total: 1500 };
      invoices = [created];
      months = [];
      return route.fulfill({ status: 201, json: created });
    });
    await page.goto('/invoices');
    await expect(page.locator('[data-invoice-from-report] .to-invoice')).toHaveText('1');

    await page.locator('[data-invoice-from-report]').click();
    const dialog = page.getByRole('dialog');
    const month = dialog.locator('[data-invoiceable-report="31"]');
    await expect(month).toContainText('Java developer · Acme');
    await expect(month).toContainText('November 2026 · Draft');
    await expect(month).toContainText('2.5 days × 600 € = 1,500.00 € before VAT');
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await month.locator('[data-invoice-this-report]').click();

    await expect(page.locator('[data-invoice="9"]')).toContainText('Draft');
    await expect(page.locator('[data-invoice="9"]')).toContainText('1,500.00');
    await expect(page.locator('[data-invoice-from-report] .to-invoice')).toHaveCount(0);
  });

  test(`writes, issues and settles an invoice (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let invoices: Record<string, unknown>[] = [{ ...base, id: 1, number: 'F2026-0001', status: 'ISSUED', overdue: true, issueDate: '2026-09-01', dueDate: '2026-10-01',
      totalExcludingVat: 600, total: 600 }];
    const calls: unknown[] = [];
    await page.route('**/api/users/billing', route => route.fulfill({ json: { vatExempt: true, vatRate: 20, complete: true } }));
    await page.route('**/api/invoices/by-freelance/1', route => route.fulfill({ json: invoices }));
    await page.route('**/api/invoices/ceilings', route => route.fulfill({ json: { year: 2026, figuresYear: 2026,
      legalForm: 'MICRO_ENTREPRISE', vatExempt: true, received: 34000, receivedLastYear: 0, outstanding: 600,
      micro: { limit: 83600, tolerance: null, revenue: 34000, percent: 40, level: 'UNDER', endsOn: null },
      vat: { limit: 37500, tolerance: 41250, revenue: 34000, percent: 90, level: 'NEAR', endsOn: null } } }));
    await page.route('**/api/invoices/contributions', route => route.fulfill({ json: { year: 2026, figuresYear: 2026,
      legalForm: 'MICRO_ENTREPRISE', activity: 'BNC_SSI', incomeTaxDischarge: false, frequency: 'QUARTERLY', rate: 25.6, officialRate: true,
      revenue: 34000, contributions: 8704, periods: [
        { from: '2026-07-01', to: '2026-09-30', revenue: 9000, contributions: 2304, dueOn: '2026-10-31', state: 'PAST' },
        { from: '2026-10-01', to: '2026-12-31', revenue: 5000, contributions: 1280, dueOn: '2027-01-31', state: 'TO_DECLARE' }] } }));
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
    // One client paid all of the last 12 months' revenue: the page says what that means.
    await expect(page.locator('[data-client-shares]')).toContainText('100 % · 600 €');
    await expect(page.locator('[data-client-dependence]')).toContainText('paid 100 % of your revenue');
    // The year's revenue received against the ceilings: near the VAT franchise threshold, it says what follows.
    await expect(page.locator('[data-ceiling="micro"]')).toContainText('34,000 € / 83,600 € · 40 %');
    await expect(page.locator('[data-ceiling="vat"] [data-ceiling-warning]')).toContainText('Near the VAT franchise threshold of €37,500');
    await expect(page.locator('[data-ceiling-outstanding]')).toContainText('€600');
    // The URSSAF declarations: the one to make now first, at the official rate.
    await expect(page.locator('[data-contributions-summary]')).toContainText('about €8,704 of contributions at 25.6 %');
    await expect(page.locator('[data-contribution-period="2026-10-01"]')).toContainText('To declare by 31 January');

    await page.locator('[data-new-invoice]').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('combobox', { name: 'Client' }).click();
    await page.getByRole('option', { name: 'Acme' }).click();
    await dialog.getByLabel('Description').fill('Audit');
    await dialog.getByLabel('Quantity').fill('2');
    await dialog.getByLabel('Unit price').fill('650');
    await expect(dialog.locator('[data-draft-totals]')).toContainText('1,300.00');
    await settleDialog(page);
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

    // A Word copy, laid out like the PDF.
    await page.route('**/api/invoices/1/docx', route => route.fulfill({ body: 'PK', contentType:
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }));
    await page.locator('[data-invoice="1"]').getByRole('button', { name: /Actions for/ }).click();
    const word = page.waitForEvent('download');
    await page.locator('[data-invoice-word]').click();
    expect((await word).suggestedFilename()).toMatch(/\.docx$/);

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

for (const width of [1280, 390]) {
  test(`marks invoices paid from a bank statement (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'fr');
    await mockWorkspaceData(page, 'fr');
    let invoices: Record<string, unknown>[] = [{ ...base, id: 1, number: 'F2026-0001', status: 'ISSUED', issueDate: '2026-11-01',
      dueDate: '2026-12-01', totalExcludingVat: 1234.56, total: 1234.56 }];
    await page.route('**/api/invoices/by-freelance/1', route => route.fulfill({ json: invoices }));
    let statement = '';
    await page.route('**/api/invoices/reconciliation', route => {
      statement = route.request().postDataJSON().content;
      return route.fulfill({ json: [
        { date: '2026-11-20', label: 'VIR SEPA FACT F2026 0001', amount: 1234.56, invoiceId: 1, invoiceNumber: 'F2026-0001', clientName: 'Acme',
          reason: 'NUMBER' },
        { date: '2026-11-21', label: 'REMISE CHEQUE', amount: 77.77, invoiceId: null, invoiceNumber: null, clientName: null, reason: null }] });
    });
    const payments: unknown[] = [];
    await page.route('**/api/invoices/1/payment', route => {
      payments.push(route.request().postDataJSON());
      invoices = [{ ...invoices[0], status: 'PAID', paidOn: '2026-11-20' }];
      return route.fulfill({ json: invoices[0] });
    });

    await page.goto('/invoices');
    await page.locator('[data-invoice-bank]').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-bank-file]').setInputFiles({ name: 'releve.csv', mimeType: 'text/csv',
      buffer: Buffer.from('Date;Libellé;Montant\n20/11/2026;VIR SEPA FACT F2026 0001;1 234,56\n') });
    await expect(dialog.locator('[data-bank-summary]')).toHaveText('2 crédit(s) lu(s), 1 rapproché(s) d\'une facture.');
    expect(statement).toContain('VIR SEPA FACT F2026 0001');
    await expect(dialog.locator('[data-bank-credit="0"]')).toContainText('Son numéro est dans le libellé');
    await expect(dialog.locator('[data-bank-credit="1"]')).toContainText('Aucune facture trouvée');
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-bank-mark-paid]').click();
    await expect(dialog).toBeHidden();
    expect(payments).toEqual([{ paidOn: '2026-11-20' }]);
    await expect(page.locator('[data-invoice="1"]')).toContainText('Payée');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
