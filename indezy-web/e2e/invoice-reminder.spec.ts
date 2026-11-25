import { expect, test } from '@playwright/test';
import { settleDialog } from './helpers/dialog';
import { wcagViolations } from './helpers/axe';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const overdue = { id: 3, kind: 'INVOICE', number: 'F2026-0003', status: 'ISSUED', overdue: true, clientId: 3, clientName: 'Acme',
  projectId: 42, projectRole: 'Java developer', activityReportId: null, issueDate: '2026-10-01', dueDate: '2026-10-31', periodStart: null,
  periodEnd: null, language: 'FR', notes: null, lines: [], totalExcludingVat: 7800, vat: 0, total: 7800, paidOn: null,
  createdAt: '2026-10-01T10:00:00', currency: 'EUR', lateFees: { daysLate: 25, amountDue: 7800, rate: 10.5, interest: 56.1, indemnity: 40 } };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  const en = language === 'en';
  test(`reminds a client of an overdue invoice, in the invoice's language (${language}, ${width}px)`, async ({ page }) => {
    test.setTimeout(60_000);
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/billing', route => route.fulfill({ json: { vatExempt: true, vatRate: 20, complete: true } }));
    await page.route('**/api/invoices/by-freelance/1', route => route.fulfill({ json: [overdue] }));
    await page.route('**/api/contacts/by-client/3', route => route.fulfill({ json: [
      { id: 2, firstName: 'Léa', lastName: 'Martin', email: 'lea@acme.example', clientId: 3, clientName: 'Acme' }] }));
    await page.route('**/api/contacts/emails/status', route => route.fulfill({ json: { available: true, sentToday: 0, dailyMax: 50 } }));
    const reminded: string[] = [];
    await page.route('**/api/invoices/3/reminded', route => {
      reminded.push(route.request().method());
      return route.fulfill({ json: { ...overdue, remindedOn: '2026-11-25' } });
    });
    const sent: unknown[] = [];
    await page.route('**/api/contacts/2/emails', route => {
      sent.push(route.request().postDataJSON());
      return route.fulfill({ json: { id: 90, occurredOn: '2026-11-25', channel: 'EMAIL', direction: 'OUTGOING' } });
    });

    await page.goto('/invoices');
    await page.locator('[data-invoice="3"]').getByRole('button', { name: en ? 'Actions for F2026-0003' : 'Actions pour F2026-0003' }).click();
    await page.locator('[data-invoice-remind]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading')).toContainText('F2026-0003');
    // The invoice is in French: so is its reminder, whatever the interface's language.
    await expect(dialog.locator('[data-reminder-subject]')).toHaveValue('Facture F2026-0003 : paiement en attente');
    const body = dialog.locator('[data-reminder-body]');
    await expect(body).toHaveValue(/Bonjour Léa,/);
    await expect(body).toHaveValue(/25 jours de retard/);
    await expect(body).toHaveValue(/art\. L441-10 et D441-5 du Code de commerce/);
    await expect(dialog.locator('[data-reminder-open]')).toHaveAttribute('href', /^mailto:lea@acme\.example\?subject=Facture/);
    await settleDialog(page);
    expect(await wcagViolations(page), 'payment reminder').toEqual([]);

    await dialog.locator('[data-reminder-send]').click();
    await expect(dialog).toHaveCount(0);
    // The invoice now says when its client was reminded.
    await expect(page.locator('[data-invoice="3"] [data-reminded-on]')).toContainText(en ? 'reminded on' : 'relancée le');
    expect(reminded).toEqual(['POST']);
    expect(sent).toEqual([expect.objectContaining({ projectId: 42, track: false, copy: true, language: 'FR',
      subject: 'Facture F2026-0003 : paiement en attente' })]);
  });
}
