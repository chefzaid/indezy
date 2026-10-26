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
        notes: [{ id: 9, title: 'Développeur Kafka', excerpt: 'Appel avec Hélène : la mission démarre en janvier.', projectId: 4 }]
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
    await expect(dialog.getByRole('option')).toHaveCount(3);
    await expect(dialog.locator('mark')).toHaveText(['Hélène', 'Hélène']);
    expect(queries).toEqual(['helene']);
    await dialog.screenshot({ path: test.info().outputPath('search.png') });

    await input.press('ArrowDown');
    await expect(dialog.getByRole('option', { selected: true })).toContainText('Hélène Durand');
    await input.press('Enter');
    await expect(page).toHaveURL(/\/contacts\/7$/);
    await expect(page.getByRole('dialog')).toBeHidden();
  });
}
