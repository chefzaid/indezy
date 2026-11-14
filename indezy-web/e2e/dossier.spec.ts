import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const language of ['en', 'fr']) {
  test(`downloads the dossier de compétences, named or anonymized (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    const asked: string[] = [];
    await page.route('**/api/users/dossier?*', route => {
      asked.push(new URL(route.request().url()).search);
      return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
    });

    await page.goto('/profile');
    await page.locator('[data-dossier]').click();
    const audit = await new AxeBuilder({ page }).include('.mat-mdc-menu-panel').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    const download = page.waitForEvent('download');
    await page.locator('[data-dossier-choice="ANONYMOUS-EN"]').click();
    expect((await download).suggestedFilename()).toBe('skills-profile-anonymous.pdf');
    expect(asked).toEqual(['?layout=ANONYMOUS&language=EN']);
  });
}
