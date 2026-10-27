import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

test('writes a rate proposal to the ESN\'s contact from a template, and keeps an own template', async ({ page, context }) => {
  test.setTimeout(60_000);
  await mockWorkspaceSession(page, 'fr');
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'fr' } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: {
    id: 1, firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.test', phone: '0600000000', availabilityDate: '2026-12-01', skills: []
  } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
  await page.route('**/api/users/security', route => route.fulfill({ json: {} }));
  await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
  await page.route('**/api/projects/42', route => route.fulfill({ json: {
    id: 42, role: 'Développeur Java', status: 'CONTACT', dailyRate: 650, clientId: 3, clientName: 'Airbus', middlemanId: 5, middlemanName: 'Alten'
  } }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/42/attachments', route => route.fulfill({ json: [] }));
  await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
  await page.route('**/api/contacts/by-client/5', route => route.fulfill({ json: [
    { id: 11, firstName: 'Léa', lastName: 'Martin', email: 'lea@alten.fr', clientName: 'Alten', clientId: 5 }
  ] }));
  await page.route('**/api/contacts/by-client/3', route => route.fulfill({ json: [] }));
  const own: Record<string, unknown>[] = [];
  await page.route('**/api/email-templates', async route => {
    if (route.request().method() === 'POST') {
      own.push({ id: own.length + 1, ...route.request().postDataJSON() });
      await route.fulfill({ status: 201, json: own.at(-1) });
    } else {
      await route.fulfill({ json: own });
    }
  });

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'E-mails' }).click();
  await page.getByRole('button', { name: 'Nouveau modèle' }).click();
  await page.getByLabel('Nom').fill('Relance ESN');
  await page.getByLabel('Objet').fill('Point sur {{role}}');
  await page.getByLabel('Message', { exact: true }).fill('Bonjour ');
  await page.getByText('{{firstName}}', { exact: true }).click();
  await page.getByRole('button', { name: 'Enregistrer' }).click();
  await expect(page.getByText('Modèle enregistré')).toBeVisible();
  expect(own).toEqual([{ id: 1, name: 'Relance ESN', subject: 'Point sur {{role}}', body: 'Bonjour {{firstName}}' }]);

  await page.goto('/projects/42');
  await page.getByRole('button', { name: 'Écrire un e-mail' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByLabel('Objet')).toHaveValue('Suite à notre échange sur la mission Développeur Java');
  await dialog.getByRole('combobox', { name: 'Modèle' }).click();
  await page.getByRole('option', { name: 'Proposition de TJM' }).click();
  await expect(dialog.getByLabel('Message', { exact: true })).toHaveValue(/Bonjour Léa,[\s\S]*chez Alten, mon TJM est de 650 € HT/);
  await expect(dialog.getByRole('link', { name: 'Ouvrir dans ma messagerie' }))
    .toHaveAttribute('href', /^mailto:lea@alten\.fr\?subject=Mon%20TJM%20pour%20la%20mission%20D%C3%A9veloppeur%20Java&body=Bonjour%20L%C3%A9a/);

  await expect(page.getByRole('listbox')).toBeHidden();
  await dialog.getByRole('combobox', { name: 'Modèle' }).click();
  await page.getByRole('option', { name: 'Relance ESN' }).click();
  await expect(dialog.getByLabel('Message', { exact: true })).toHaveValue('Bonjour Léa');
  await dialog.getByRole('button', { name: 'Copier' }).click();
  await expect(page.getByText('Message copié')).toBeVisible();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('Point sur Développeur Java\n\nBonjour Léa');
});
