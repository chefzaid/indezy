import { expect, test } from '@playwright/test';
import type { ContactRelation } from '../src/app/models/contact.models';
import { mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`links contacts who work together and draws the client's org chart (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    const alice = { id: 1, firstName: 'Alice', lastName: 'Martin', clientId: 1, clientName: 'Acme' };
    const bob = { id: 2, firstName: 'Bob', lastName: 'Durand', clientId: 1, clientName: 'Acme', managerId: 1, managerName: 'Alice Martin' };
    const nadia = { id: 3, firstName: 'Nadia', lastName: 'Kaci', clientId: 2, clientName: 'Esn Conseil' };
    let relations: ContactRelation[] = [];
    await page.route('**/api/contacts/by-freelance/1', route => route.fulfill({ json: [alice, bob, nadia] }));
    await page.route('**/api/contacts/by-client/1', route => route.fulfill({ json: [alice, bob] }));
    await page.route('**/api/contacts/1', route => route.fulfill({ json: alice }));
    await page.route('**/api/contacts/1/interactions', route => route.fulfill({ json: { interactions: [], asked: 0, answered: 0 } }));
    await page.route('**/api/contacts/1/relations', async route => {
      if (route.request().method() === 'PUT') {
        const { otherId, note } = route.request().postDataJSON();
        const relation = { contactId: 1, otherId, otherName: 'Nadia Kaci', otherClientId: 2, otherClientName: 'Esn Conseil',
          otherClientIsFinal: false, note };
        relations = [relation];
        await route.fulfill({ json: relation });
        return;
      }
      await route.fulfill({ json: relations });
    });
    await page.route('**/api/clients/1', route => route.fulfill({ json: { id: 1, companyName: 'Acme', city: 'Lyon', isFinal: true } }));
    await page.route('**/api/clients/1/contact-relations', route => route.fulfill({ json: relations }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));

    await page.goto('/contacts/1');
    const network = page.locator('app-contact-network');
    await expect(network).toContainText(en ? 'Nobody' : 'Personne');
    await expect(network.getByRole('link', { name: 'Bob Durand' })).toBeVisible();
    await expect(network).toContainText(en ? 'No one linked yet.' : "Personne n'est lié pour l'instant.");

    const form = network.getByRole('form');
    await form.getByRole('combobox', { name: en ? 'Person' : 'Personne' }).click();
    await page.getByRole('option', { name: 'Nadia Kaci · Esn Conseil' }).click();
    await expect(page.getByRole('listbox')).toBeHidden();
    await form.getByRole('textbox').fill('Account manager for the mission');
    await form.getByRole('button', { name: en ? 'Link' : 'Lier' }).click();
    await expect(network.locator('.relation')).toHaveCount(1);
    await expect(network.locator('.relation')).toContainText('Esn Conseil · ESN');
    expect(relations[0].note).toBe('Account manager for the mission');
    await network.screenshot({ path: test.info().outputPath('network.png') });

    await page.goto('/clients/1?tab=contacts');
    const chart = page.locator('app-client-org-chart');
    await expect(chart.locator('.org-name')).toHaveText(['Alice Martin', 'Bob Durand']);
    await expect(chart.locator('.org-level .org-level .org-name')).toHaveText('Bob Durand');
    await expect(chart.locator('.org-link')).toContainText('Nadia Kaci · Esn Conseil');
    await chart.screenshot({ path: test.info().outputPath('org-chart.png') });
  });
}
