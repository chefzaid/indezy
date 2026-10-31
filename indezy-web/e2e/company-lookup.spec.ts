import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`fills a new client in from the public company directory (${language})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    const searches: string[] = [];
    await page.route('**/api/companies/search?*', route => {
      searches.push(new URL(route.request().url()).searchParams.get('q') ?? '');
      return route.fulfill({ json: [
        { siren: '542051180', name: 'TOTALENERGIES SE', address: '2 PLACE JEAN MILLIER', postalCode: '92400', city: 'COURBEVOIE',
          nafCode: '70.10Z', legalForm: 'SA', vatNumber: 'FR59542051180', closed: false },
        { siren: '123456789', name: 'TOTAL ANCIENNE', city: 'LYON', closed: true }] });
    });

    await page.goto('/clients/new');
    const name = page.locator('input[formControlName="companyName"]');
    await name.fill('total');
    const option = page.locator('[data-company="542051180"]');
    await expect(option).toContainText('542051180 · SA · COURBEVOIE · NAF 70.10Z');
    await expect(page.locator('[data-company="123456789"]')).toContainText(language === 'en' ? 'closed' : 'fermée');
    const audit = await new AxeBuilder({ page }).include('.mat-mdc-autocomplete-panel').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await option.click();

    await expect(name).toHaveValue('TOTALENERGIES SE');
    await expect(page.locator('input[formControlName="city"]')).toHaveValue('Courbevoie');
    await expect(page.locator('textarea[formControlName="address"]')).toHaveValue('2 PLACE JEAN MILLIER\n92400 Courbevoie');
    await expect(page.locator('input[formControlName="siren"]')).toHaveValue('542051180');
    await expect(page.locator('input[formControlName="vatNumber"]')).toHaveValue('FR59542051180');
    await page.waitForTimeout(500);
    expect(searches).toEqual(['total']);
  });
}
