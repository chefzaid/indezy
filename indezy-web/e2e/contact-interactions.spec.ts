import { expect, test } from '@playwright/test';
import type { ContactInteraction } from '../src/app/models/contact.models';
import { mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`records exchanges with a contact and reads the relationship from them (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    let interactions: ContactInteraction[] = [];
    let nextId = 1;
    const contact = { id: 3, firstName: 'Claire', lastName: 'Martin', clientId: 1, clientName: 'Acme', freelanceId: 1 };
    const history = (): object => {
      const sorted = [...interactions].sort((a, b) => b.occurredOn.localeCompare(a.occurredOn));
      const known = sorted.filter(i => i.direction === 'OUTGOING' && i.replied != null);
      const answered = known.filter(i => i.replied).length;
      return {
        interactions: sorted,
        lastContactedOn: sorted[0]?.occurredOn ?? null,
        asked: known.length,
        answered,
        responseRate: known.length ? Math.round(answered * 100 / known.length) : null,
        preferredChannel: sorted.find(i => i.direction === 'INCOMING' || i.replied)?.channel ?? null
      };
    };
    await page.route('**/api/clients/by-freelance/1', route => route.fulfill({ json: [{ id: 1, companyName: 'Acme', isFinal: true }] }));
    await page.route('**/api/contacts/by-freelance/1', route => route.fulfill({ json: [
      { ...contact, lastContactedOn: (history() as { lastContactedOn: string | null }).lastContactedOn }
    ] }));
    await page.route('**/api/contacts/3', route => route.fulfill({ json: contact }));
    await page.route('**/api/contacts/3/interactions', async route => {
      if (route.request().method() === 'POST') {
        const created = { ...route.request().postDataJSON(), id: nextId++, contactId: 3 };
        interactions.push(created);
        await route.fulfill({ status: 201, json: created });
        return;
      }
      await route.fulfill({ json: history() });
    });
    await page.route('**/api/contacts/3/interactions/*', async route => {
      const id = Number(route.request().url().split('/').pop());
      if (route.request().method() === 'DELETE') {
        interactions = interactions.filter(i => i.id !== id);
        await route.fulfill({ status: 204 });
        return;
      }
      const changed = { ...route.request().postDataJSON(), id, contactId: 3 };
      interactions = interactions.map(i => i.id === id ? changed : i);
      await route.fulfill({ json: changed });
    });

    await page.goto('/contacts');
    const lastContactCell = page.locator('td.mat-column-lastContact');
    await expect(lastContactCell).toHaveText('—');
    await page.getByText('Claire Martin').click();
    await expect(page).toHaveURL(/\/contacts\/3$/);

    const card = page.locator('app-contact-interactions');
    await expect(card).toContainText(en ? 'Never' : 'Jamais');
    await card.getByRole('button', { name: en ? 'Record an exchange' : 'Noter un échange' }).click();
    const form = card.getByRole('form');
    await form.getByRole('combobox', { name: en ? 'Channel' : 'Canal' }).click();
    await page.getByRole('option', { name: 'LinkedIn', exact: true }).click();
    await expect(page.getByRole('listbox')).toBeHidden();
    await form.getByRole('combobox', { name: en ? 'Answer' : 'Réponse' }).click();
    await page.getByRole('option', { name: en ? 'Answered' : 'A répondu', exact: true }).click();
    await expect(page.getByRole('listbox')).toBeHidden();
    await form.getByRole('textbox', { name: en ? 'Summary (optional)' : 'Résumé (facultatif)' }).fill('Sent my availability');
    await form.getByRole('button', { name: en ? 'Save' : 'Sauvegarder', exact: true }).click();
    await expect(card.locator('.interaction')).toHaveCount(1);
    await expect(card).toContainText('100');
    expect(interactions[0]).toMatchObject({ channel: 'LINKEDIN', direction: 'OUTGOING', replied: true, summary: 'Sent my availability' });

    await card.getByRole('button', { name: en ? 'Record an exchange' : 'Noter un échange' }).click();
    await form.getByLabel('Date').fill('2026-01-15');
    await form.getByRole('radio', { name: en ? 'From them' : 'De sa part' }).click();
    await expect(form.getByRole('combobox', { name: en ? 'Answer' : 'Réponse' })).toBeHidden();
    await form.getByRole('button', { name: en ? 'Save' : 'Sauvegarder', exact: true }).click();
    await expect(card.locator('.interaction')).toHaveCount(2);
    expect(interactions[1]).toMatchObject({ occurredOn: '2026-01-15', channel: 'EMAIL', direction: 'INCOMING', replied: null });

    const latest = card.locator('.interaction').first();
    await latest.getByRole('button', { name: en ? /Edit the exchange/ : /Modifier l'échange/ }).click();
    await form.getByRole('combobox', { name: en ? 'Answer' : 'Réponse' }).click();
    await page.getByRole('option', { name: en ? 'No answer' : 'Sans réponse', exact: true }).click();
    await expect(page.getByRole('listbox')).toBeHidden();
    await form.getByRole('button', { name: en ? 'Save' : 'Sauvegarder', exact: true }).click();
    await expect(latest.locator('.answer-pill')).toHaveText(en ? 'No answer' : 'Sans réponse');
    await expect(card.locator('.interaction-summary')).toContainText(en ? 'Email' : 'E-mail');
    await card.screenshot({ path: test.info().outputPath('interactions.png') });

    await card.locator('.interaction').last().getByRole('button', { name: en ? /Delete the exchange/ : /Supprimer l'échange/ }).click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Confirm' : 'Confirmer' }).click();
    await expect(card.locator('.interaction')).toHaveCount(1);
    expect(interactions).toHaveLength(1);

    await page.goto('/contacts');
    await expect(lastContactCell).not.toHaveText('—');
  });
}
