import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`tags and deletes picked opportunities, and deletes picked clients (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1, firstName: 'Workspace' } }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/sources/by-freelance/1', route => route.fulfill({ json: [] }));
    let projects = [41, 42, 43].map((id, i) => ({
      id, role: ['Java developer', 'Data engineer', 'Cloud architect'][i], dailyRate: 600, status: 'CONTACT', clientName: 'Acme',
      techStack: 'Java'
    }));
    const changes: { projectIds: number[]; action: string; tag?: string }[] = [];
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: projects }));
    await page.route('**/api/projects/bulk', async route => {
      const change = route.request().postDataJSON();
      changes.push(change);
      if (change.action === 'DELETE') {
        projects = projects.filter(p => !change.projectIds.includes(p.id));
      }
      await route.fulfill({ json: { updated: change.projectIds.length } });
    });
    let clients = [
      { id: 1, companyName: 'Acme', city: 'Lyon', isFinal: true },
      { id: 2, companyName: 'Zenika', city: 'Paris', isFinal: false }
    ];
    let deletedClients: number[] = [];
    await page.route('**/api/clients/by-freelance/1', route => route.fulfill({ json: clients }));
    await page.route('**/api/clients/bulk-delete', async route => {
      deletedClients = route.request().postDataJSON().ids;
      clients = clients.filter(client => client.id !== 2);
      await route.fulfill({ json: { deleted: 1, kept: 1 } });
    });

    await page.goto('/projects');
    const select = en ? 'Select' : 'Sélectionner';
    await page.getByRole('checkbox', { name: `${select} Java developer` }).check();
    await page.getByRole('checkbox', { name: `${select} Cloud architect` }).check();
    const bar = page.getByRole('toolbar');
    await expect(bar).toContainText(en ? '2 selected' : '2 sélectionnée(s)');

    await bar.getByRole('button', { name: en ? 'Add a tag' : 'Ajouter un tag' }).click();
    await expect(bar.getByRole('textbox')).toBeFocused();
    await bar.getByRole('textbox').fill('Kafka');
    await bar.getByRole('textbox').press('Enter');
    await expect.poll(() => changes.length).toBe(1);
    expect(changes[0]).toEqual({ action: 'TAG', tag: 'Kafka', projectIds: [41, 43] });
    await bar.screenshot({ path: test.info().outputPath('project-selection.png') });

    await bar.getByRole('button', { name: en ? 'Delete' : 'Supprimer', exact: true }).click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Delete' : 'Supprimer' }).click();
    await expect(page.locator('.project-title')).toHaveText(['Data engineer']);
    expect(changes[1]).toEqual({ action: 'DELETE', projectIds: [41, 43] });
    await expect(page.getByRole('toolbar')).toBeHidden();

    await page.goto('/clients');
    await page.getByRole('checkbox', { name: en ? 'Select all' : 'Tout sélectionner' }).check();
    const clientBar = page.getByRole('toolbar');
    await expect(clientBar).toContainText(en ? '2 selected' : '2 sélectionné(s)');
    await clientBar.getByRole('button', { name: en ? 'Delete' : 'Supprimer' }).click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Delete' : 'Supprimer' }).click();
    await expect(page.getByText(en ? '1 clients deleted; 1 kept because missions are linked to them'
      : '1 client(s) supprimé(s) ; 1 conservé(s) car des missions y sont liées')).toBeVisible();
    expect(deletedClients.sort()).toEqual([1, 2]);
    await expect(page.locator('tr.client-row')).toHaveCount(1);
  });
}
