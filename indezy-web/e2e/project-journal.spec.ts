import { expect, test } from '@playwright/test';
import type { ContactInteraction } from '../src/app/models/contact.models';
import { mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`keeps the notes and the exchanges about an opportunity in its journal (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
    await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
    await page.route('**/api/projects/42', route => route.fulfill({ json: {
      id: 42, role: 'Java developer', status: 'INTERVIEW', clientId: 3, clientName: 'Acme', middlemanId: 8, middlemanName: 'Esn',
      dailyRate: 650
    } }));
    await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [
      { id: 5, projectId: 42, content: 'Second **interview** booked', createdAt: '2026-10-21T09:30:00' }
    ] }));
    await page.route('**/api/interview-steps/by-project/42/ordered', route => route.fulfill({ json: [] }));
    await page.route('**/api/contacts/by-client/3', route => route.fulfill({ json: [{ id: 2, firstName: 'Alice', clientId: 3, clientName: 'Acme' }] }));
    await page.route('**/api/contacts/by-client/8', route => route.fulfill({ json: [
      { id: 9, firstName: 'Nadia', lastName: 'Kaci', clientId: 8, clientName: 'Esn' }
    ] }));
    let exchanges: ContactInteraction[] = [{ id: 30, contactId: 2, contactName: 'Alice', occurredOn: '2026-10-22', channel: 'EMAIL',
      direction: 'INCOMING', summary: 'Asked for my availability', projectId: 42 }];
    await page.route('**/api/projects/42/interactions', route => route.fulfill({ json: exchanges }));
    const recorded: ContactInteraction[] = [];
    await page.route('**/api/contacts/9/interactions', async route => {
      const exchange = { ...route.request().postDataJSON(), id: 31, contactId: 9, contactName: 'Nadia Kaci' };
      recorded.push(exchange);
      exchanges = [exchange, ...exchanges];
      await route.fulfill({ status: 201, json: exchange });
    });

    await page.goto('/projects/42');
    const journal = page.locator('app-project-journal');
    const entries = journal.locator('.journal-entry');
    await expect(entries).toHaveCount(2);
    await expect(entries.first()).toContainText('Alice');
    await expect(entries.first()).toContainText('Asked for my availability');
    await expect(entries.nth(1)).toContainText('Second interview booked');

    await journal.getByRole('button', { name: en ? 'Record an exchange' : 'Noter un échange' }).click();
    const form = journal.getByRole('form');
    await form.getByRole('combobox', { name: en ? 'With' : 'Avec' }).click();
    await page.getByRole('option', { name: 'Nadia Kaci · Esn' }).click();
    await expect(page.getByRole('listbox')).toBeHidden();
    await form.getByLabel('Date').fill('2026-10-25');
    await form.getByRole('textbox', { name: en ? 'Summary (optional)' : 'Résumé (facultatif)' }).fill('Negotiated the rate');
    await form.getByRole('button', { name: en ? 'Save' : 'Sauvegarder', exact: true }).click();

    await expect(entries).toHaveCount(3);
    await expect(entries.first()).toContainText('Nadia Kaci');
    expect(recorded[0]).toMatchObject({ occurredOn: '2026-10-25', channel: 'EMAIL', direction: 'OUTGOING', replied: null,
      summary: 'Negotiated the rate', projectId: 42 });
    await journal.screenshot({ path: test.info().outputPath('journal.png') });
  });
}
