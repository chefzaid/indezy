import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`uses workspace shortcuts without interrupting typing or dialogs (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    if (language === 'fr') { await page.setViewportSize({ width: 390, height: 844 }); }
    await mockWorkspaceSession(page, language);
    let project = { id: 42, role: 'Java developer', status: 'CONTACT', dailyRate: 650, clientName: 'Acme' };
    const changes: string[] = [];
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
    await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
    await page.route('**/api/clients/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/sources/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/seasons/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [project] }));
    await page.route('**/api/projects/42', route => route.fulfill({ json: project }));
    await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
    await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/42/status?*', route => {
      const status = new URL(route.request().url()).searchParams.get('status')!;
      changes.push(status);
      project = { ...project, status };
      return route.fulfill({ json: project });
    });
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
    await page.route('**/api/projects/kanban/1*', route => route.fulfill({ json: {
      columnOrder: ['CONTACT', 'INTERVIEW', 'OFFER', 'WON', 'LOST'],
      columns: { [project.status]: [{ ...project, projectId: project.id }] }
    } }));

    await page.goto('/dashboard');
    await expect(page.getByRole('radio', { name: /Kanban/ })).toBeVisible();
    await page.keyboard.press('Alt+Shift+KeyF');
    const search = page.getByRole('textbox', { name: language === 'en' ? 'Search' : 'Recherche', exact: true });
    await expect(search).toBeFocused();
    await search.fill('Java');
    await page.keyboard.press('Alt+Shift+KeyN');
    await expect(page).toHaveURL(/\/projects$/);
    await expect(search).toHaveValue('Java');
    await page.getByRole('heading', { level: 1 }).click();
    await page.keyboard.press('Alt+Shift+KeyF');
    await expect(search).toBeFocused();
    await page.getByRole('heading', { level: 1 }).click();
    await page.keyboard.press('Alt+Shift+KeyN');
    await expect(page).toHaveURL(/\/projects\/new$/);
    await expect(page.getByRole('textbox', { name: language === 'en' ? 'Role / Position' : 'Rôle / Poste', exact: true })).toBeVisible();
    await page.keyboard.press('Alt+Shift+KeyH');
    const help = page.getByRole('dialog', { name: language === 'en' ? 'Keyboard shortcuts' : 'Raccourcis clavier' });
    await expect(help).toBeVisible();
    await page.keyboard.press('Alt+Shift+KeyN');
    await expect(help).toBeVisible();
    await help.screenshot({ path: test.info().outputPath('shortcut-help.png') });
    await page.keyboard.press('Escape');
    await expect(help).not.toBeVisible();

    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    const card = page.locator('.card-content');
    await card.focus();
    await expect(card).toBeFocused();
    await page.keyboard.press('Alt+Shift+KeyM');
    await expect(page.getByRole('menu')).toBeVisible();
    await page.getByRole('menuitem', { name: language === 'en' ? 'Offer' : 'Offre', exact: true }).focus();
    await page.keyboard.press('Enter');
    await expect.poll(() => changes).toEqual(['OFFER']);
    await expect(page.getByRole('menu')).not.toBeVisible();
    await expect(page.locator('[data-project-status]')).toBeEnabled();
    await expect(page.locator('[data-project-status]')).toContainText(language === 'en' ? 'Offer' : 'Offre');
    await page.keyboard.press('Alt+Shift+KeyM');
    await page.getByRole('menuitem', { name: language === 'en' ? 'Lost / Dropped' : 'Perdu / Abandonné', exact: true }).focus();
    await page.keyboard.press('Enter');
    const reason = page.getByRole('dialog');
    await expect(reason).toBeVisible();
    await page.keyboard.press('Alt+Shift+KeyN');
    await reason.getByRole('button', { name: language === 'en' ? 'Cancel' : 'Annuler', exact: true }).press('Enter');
    await expect(reason).not.toBeVisible();
    expect(changes).toEqual(['OFFER']);
    await expect(page).toHaveURL(/\/projects\/42\?focus=status$/);
  });
}
