import { expect, test } from '@playwright/test';
import { wcagViolations } from './helpers/axe';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const draft = { id: 5, contactId: 3, contact: 'Inès Roy', company: 'Acme', projectId: 42, subject: 'Re: Dispo en janvier ?',
  body: 'Bonjour Inès,\n\nJe suis disponible le 5 janvier.', at: '2026-11-24T10:00:00', answer: true };

for (const language of ['en', 'fr'] as const) {
  const en = language === 'en';
  test(`sends a prepared answer from the dashboard once confirmed (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language, inactivityDays: 14, followUpDays: 5 } }));
    let drafts = [draft];
    await page.route('**/api/ai/autopilot', route => route.fulfill({ json: { on: false, offers: [], drafts } }));
    const sent: unknown[] = [];
    await page.route('**/api/contacts/3/emails', route => {
      sent.push(route.request().postDataJSON());
      return route.fulfill({ json: { id: 90, occurredOn: '2026-11-25', channel: 'EMAIL', direction: 'OUTGOING', summary: draft.subject } });
    });
    const dismissed: string[] = [];
    await page.route('**/api/ai/autopilot/drafts/5', route => {
      dismissed.push(route.request().method());
      drafts = [];
      return route.fulfill({ status: 204 });
    });

    await page.goto('/dashboard');
    const queue = page.locator('[data-autopilot-queue]');
    await expect(queue).toContainText(en ? 'Prepared for you' : 'Préparé pour vous');
    await expect(queue).toContainText('Re: Dispo en janvier ?');
    await page.waitForLoadState('networkidle');
    expect(await wcagViolations(page), 'dashboard with a prepared answer').toEqual([]);

    const send = queue.locator('[data-autopilot-send="5"]');
    await expect(send).toHaveText(en ? 'Send from Indezy' : 'Envoyer depuis Indezy');
    // Cancelled: nothing leaves.
    await send.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(en ? 'Send this email?' : 'Envoyer cet e-mail ?');
    await expect(dialog).toContainText('Inès Roy');
    await dialog.getByRole('button', { name: en ? 'Cancel' : 'Annuler' }).click();
    await expect(dialog).toHaveCount(0);
    expect(sent).toEqual([]);

    await send.click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Send from Indezy' : 'Envoyer depuis Indezy' }).click();
    await expect(page.getByText(en ? 'Email sent to Inès Roy' : 'E-mail envoyé à Inès Roy')).toBeVisible();
    await expect(queue.locator('[data-autopilot-send="5"]')).toHaveCount(0);
    expect(sent).toEqual([expect.objectContaining({ projectId: 42, subject: 'Re: Dispo en janvier ?', body: draft.body, track: false,
      language: en ? 'EN' : 'FR' })]);
    expect(dismissed).toEqual(['DELETE']);
  });
}
