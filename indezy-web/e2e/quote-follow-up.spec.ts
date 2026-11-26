import { expect, test } from '@playwright/test';
import { settleDialog } from './helpers/dialog';
import { wcagViolations } from './helpers/axe';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const sent = { id: 2, number: 'D2026-0002', status: 'SENT', expired: false, clientId: 3, clientName: 'Acme', projectId: 42, projectRole: null,
  issueDate: '2026-11-02', validityDays: 30, validUntil: '2026-12-02', depositPercent: null, language: 'FR', notes: null, lines: [], vat: 0,
  totalExcludingVat: 12500, total: 12500, decidedOn: null, invoiceId: null, depositInvoiceId: null, createdAt: '2026-11-02T10:00:00' };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  const en = language === 'en';
  test(`follows up a sent quote, in the quote's language (${language}, ${width}px)`, async ({ page }) => {
    test.setTimeout(60_000);
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/billing', route => route.fulfill({ json: { vatExempt: true, vatRate: 20, complete: true } }));
    await page.route('**/api/quotes', route => route.fulfill({ json: [sent] }));
    await page.route('**/api/contacts/by-client/3', route => route.fulfill({ json: [
      { id: 2, firstName: 'Léa', lastName: 'Martin', email: 'lea@acme.example', clientId: 3, clientName: 'Acme' }] }));
    await page.route('**/api/contacts/emails/status', route => route.fulfill({ json: { available: true, sentToday: 0, dailyMax: 50 } }));
    const emails: unknown[] = [];
    await page.route('**/api/contacts/2/emails', route => {
      emails.push(route.request().postDataJSON());
      return route.fulfill({ json: { id: 91, occurredOn: '2026-11-26', channel: 'EMAIL', direction: 'OUTGOING' } });
    });

    await page.goto('/quotes');
    await page.locator('[data-quote="2"] button[aria-haspopup="menu"]').click();
    await page.locator('[data-follow-up-quote]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading')).toContainText('D2026-0002');
    await expect(dialog.locator('[data-client-email-subject]')).toHaveValue('Devis D2026-0002 : votre retour');
    await expect(dialog.locator('[data-client-email-body]')).toHaveValue(/Bonjour Léa,/);
    await settleDialog(page);
    expect(await wcagViolations(page), 'quote follow-up').toEqual([]);
    await dialog.locator('[data-client-email-send]').click();
    await expect(dialog).toHaveCount(0);
    expect(emails).toEqual([expect.objectContaining({ projectId: 42, language: 'FR', subject: 'Devis D2026-0002 : votre retour' })]);
    await expect(page.getByText(en ? 'Email sent to Léa Martin' : 'E-mail envoyé à Léa Martin')).toBeVisible();
  });
}
