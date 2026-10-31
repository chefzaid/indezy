import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`warns that a client is under insolvency proceedings, on its page and its opportunity (${language})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    await page.route('**/api/clients/3/health', route => route.fulfill({ json: {
      checked: true, closed: false, procedure: 'REDRESSEMENT', procedureOn: '2026-05-03' } }));

    await page.goto('/clients/3');
    const warning = page.locator('[data-company-health]');
    await expect(warning).toContainText(language === 'en' ? 'Acme is under insolvency proceedings (receivership).'
      : 'Acme fait l\'objet d\'une procédure collective (redressement judiciaire).');
    await expect(warning).toContainText(language === 'en' ? 'May 3, 2026' : '3 mai 2026');
    const audit = await new AxeBuilder({ page }).include('[data-company-health]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);

    await page.goto('/projects/42');
    await expect(page.locator('[data-company-health]')).toContainText('Acme');
  });
}
