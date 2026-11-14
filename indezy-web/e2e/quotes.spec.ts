import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

const base = { status: 'DRAFT', expired: false, number: null, clientId: 3, clientName: 'Acme', projectId: null, projectRole: null,
  issueDate: null, validityDays: 30, validUntil: null, depositPercent: null, language: 'FR', notes: null, lines: [], vat: 0,
  decidedOn: null, invoiceId: null, createdAt: '2026-11-14T10:00:00' };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`writes, sends, accepts and invoices a quote (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    let quotes: Record<string, unknown>[] = [{ ...base, id: 1, number: 'D2026-0001', status: 'REFUSED', decidedOn: '2026-11-02',
      totalExcludingVat: 900, total: 900 }];
    const calls: unknown[] = [];
    await page.route('**/api/users/billing', route => route.fulfill({ json: { vatExempt: true, vatRate: 20, complete: true } }));
    await page.route('**/api/quotes', route => {
      if (route.request().method() === 'GET') {
        return route.fulfill({ json: quotes });
      }
      calls.push(route.request().postDataJSON());
      const created = { ...base, id: 2, ...route.request().postDataJSON(), totalExcludingVat: 4000, total: 4000 };
      quotes = [created, ...quotes];
      return route.fulfill({ status: 201, json: created });
    });
    await page.route('**/api/quotes/2/send', route => {
      calls.push('send');
      return route.fulfill({ json: { ...quotes[0], status: 'SENT', number: 'D2026-0002', issueDate: '2026-11-14', validUntil: '2026-11-29' } });
    });
    await page.route('**/api/quotes/2/decision', route => {
      calls.push(route.request().postDataJSON());
      return route.fulfill({ json: { ...quotes[0], status: 'ACCEPTED', number: 'D2026-0002', decidedOn: '2026-11-14' } });
    });
    await page.route('**/api/quotes/2/invoice', route => {
      calls.push('invoice');
      return route.fulfill({ status: 201, json: { id: 40, kind: 'INVOICE', status: 'DRAFT', quoteNumber: 'D2026-0002' } });
    });
    await page.route('**/api/invoices/by-freelance/1', route => route.fulfill({ json: [] }));

    await page.goto('/quotes');
    await expect(page.locator('[data-quote="1"]')).toContainText(en ? 'Refused' : 'Refusé');
    await expect(page.locator('[data-quotes-acceptance]')).toHaveText('0 %');

    await page.locator('[data-new-quote]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { name: en ? 'New quote' : 'Nouveau devis' })).toBeVisible();
    await dialog.getByRole('combobox', { name: en ? 'Client' : 'Client' }).click();
    await page.getByRole('option', { name: 'Acme' }).click();
    await dialog.locator('[data-validity-days] input').fill('15');
    await dialog.locator('[data-deposit-percent] input').fill('30');
    await dialog.locator('[data-line="0"] input').first().fill(en ? 'Architecture audit' : "Audit de l'architecture");
    await dialog.locator('[data-line="0"] input').nth(1).fill('5');
    await dialog.locator('[data-line="0"] input').nth(2).fill('800');
    await expect(dialog.locator('[data-draft-totals]')).toContainText(en ? '4,000.00' : '4 000,00');
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-save-draft]').click();
    const quote = page.locator('[data-quote="2"]');
    await expect(quote).toContainText(en ? '30 % deposit' : 'acompte de 30 %');

    await quote.getByRole('button', { name: en ? /Actions for/ : /Actions/ }).click();
    await page.locator('[data-send-quote]').click();
    await page.locator('[data-invoice-date-confirm]').click();
    await expect(quote).toContainText('D2026-0002');
    await expect(quote).toContainText(en ? 'Sent' : 'Envoyé');

    await quote.getByRole('button', { name: en ? /Actions for/ : /Actions/ }).click();
    await page.locator('[data-accept-quote]').click();
    await expect(quote).toContainText(en ? 'Accepted' : 'Accepté');
    await expect(page.locator('[data-quotes-acceptance]')).toHaveText('50 %');

    await quote.getByRole('button', { name: en ? /Actions for/ : /Actions/ }).click();
    await page.locator('[data-invoice-quote]').click();
    await expect(page).toHaveURL(/\/invoices$/);

    expect(calls[0]).toEqual({ clientId: 3, projectId: null, language: 'FR', notes: null, validityDays: 15, depositPercent: 30,
      lines: [{ description: en ? 'Architecture audit' : "Audit de l'architecture", quantity: 5, unitPrice: 800, vatRate: 0 }] });
    expect(calls.slice(1)).toEqual(['send', { accepted: true }, 'invoice']);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
