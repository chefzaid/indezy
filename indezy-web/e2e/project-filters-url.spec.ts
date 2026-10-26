import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`keeps the projects list's filters in its address (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [
      { id: 1, role: 'Java developer', dailyRate: 600, status: 'CONTACT', techStack: 'Java' },
      { id: 2, role: 'Kafka engineer', dailyRate: 700, status: 'OFFER', techStack: 'Kafka', archivedAt: '2026-10-01T03:30:00' },
      { id: 3, role: 'Go developer', dailyRate: 450, status: 'CONTACT', techStack: 'Go' }
    ] }));

    await page.goto('/projects?min=500&archived=active');
    const titles = page.locator('.project-title');
    await expect(titles).toHaveText(['Java developer']);

    if (!en) {
      await page.getByRole('button', { name: /Filtres/ }).first().click();
    }
    await page.getByRole('combobox', { name: 'Archive' }).click();
    await page.getByRole('option', { name: en ? 'Archived' : 'Archivées' }).click();
    await expect(titles).toHaveText(['Kafka engineer']);
    await expect(page).toHaveURL(/\/projects\?min=500&archived=archived$/);

    await page.reload();
    await expect(titles).toHaveText(['Kafka engineer']);
    await page.goto('/projects');
    await expect(titles).toHaveCount(3);
  });
}
