import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`searches the whole workspace from anywhere and opens a hit (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
    const queries: string[] = [];
    await page.route('**/api/search?*', route => {
      queries.push(new URL(route.request().url()).searchParams.get('q')!);
      return route.fulfill({ json: {
        projects: [{ id: 4, title: 'Développeur Kafka', subtitle: 'Société Générale' }],
        clients: [],
        contacts: [{ id: 7, title: 'Hélène Durand', subtitle: 'Société Générale' }],
        notes: [{ id: 9, title: 'Développeur Kafka', excerpt: 'Appel avec Hélène : la mission démarre en janvier.', projectId: 4 }],
        members: [{ id: 12, title: 'Hélène Martin', subtitle: '@helene_m', handle: 'helene_m' }],
        posts: []
      } });
    });
    await page.route('**/api/contacts/7', route => route.fulfill({ json: { id: 7, firstName: 'Hélène', lastName: 'Durand',
      clientId: 2, clientName: 'Société Générale' } }));
    await page.route('**/api/contacts/7/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/contacts/by-freelance/1', route => route.fulfill({ json: [] }));

    await page.goto('/dashboard');
    if (en) {
      await page.locator('body').press('Alt+Shift+KeyS');
    } else {
      await page.getByRole('button', { name: 'Tout rechercher' }).click();
    }
    const dialog = page.getByRole('dialog');
    const input = dialog.getByRole('combobox');
    await expect(input).toBeFocused();
    await input.fill('helene');
    await expect(dialog.getByRole('option')).toHaveCount(4);
    await expect(dialog.locator('mark')).toHaveText(['Hélène', 'Hélène', 'Hélène', 'helene']);
    await expect(dialog.getByRole('group', { name: en ? 'Members' : 'Membres' })).toContainText('Martin');
    expect(queries).toEqual(['helene']);
    await dialog.screenshot({ path: test.info().outputPath('search.png') });

    await input.press('ArrowDown');
    await expect(dialog.getByRole('option', { selected: true })).toContainText('Hélène Durand');
    await input.press('Enter');
    await expect(page).toHaveURL(/\/contacts\/7$/);
    await expect(page.getByRole('dialog')).toBeHidden();
  });
}

test('searches the opportunities and notes by meaning with the AI, and opens what it found (en)', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
  await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.route('**/api/ai/status', route => route.fulfill({ json: { available: true, reason: null, ownKey: false, model: null, models: {},
    spentUsd: 0, limitUsd: 10, taskModels: {} } }));
  await page.route('**/api/search?*', route => route.fulfill({ json: { projects: [], clients: [], contacts: [], notes: [], members: [], posts: [] } }));
  const asked: string[] = [];
  await page.route('**/api/ai/search', route => {
    asked.push(route.request().postDataJSON().q);
    return route.fulfill({ json: [{ kind: 'NOTE', projectId: 4, noteId: 9, title: 'Lead Java · Banque Exemple', excerpt: 'Le client a trouvé notre TJM trop élevé',
      why: 'The rate was found too high.' }] });
  });
  await page.route('**/api/projects/4', route => route.fulfill({ json: { id: 4, role: 'Lead Java', status: 'LOST', freelanceId: 1 } }));

  await page.goto('/dashboard');
  await page.locator('body').press('Alt+Shift+KeyS');
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('combobox').fill('missions lost on price');
  await dialog.locator('[data-ai-search-button]').click();
  await expect.poll(() => asked).toEqual(['missions lost on price']);
  const hit = dialog.locator('[data-ai-hit="NOTE-9"]');
  await expect(hit).toContainText('The rate was found too high.');
  await hit.click();
  await expect(page).toHaveURL(/\/projects\/4$/);
});
