import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const report = (applied: boolean) => ({ applied, sections: [
  { kind: 'SOURCES', total: 2, added: 0, duplicates: 2, invalid: 0, items: [] },
  { kind: 'CLIENTS', total: 2, added: 1, duplicates: 0, invalid: 1, items: [
    { label: 'Acme', status: 'NEW' }, { label: 'Globex', status: 'INVALID', reason: 'City is required' }] },
  { kind: 'PROJECTS', total: 1, added: 1, duplicates: 0, invalid: 0, items: [{ label: 'Java lead', status: 'NEW' }] },
  { kind: 'SEASONS', total: 0, added: 0, duplicates: 0, invalid: 0, items: [] }
] });

for (const width of [1280, 390]) {
  test(`previews an export file, then imports it (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    const calls: string[] = [];
    await page.route('**/api/users/import/preview', route => {
      calls.push('preview');
      return route.fulfill({ json: report(false) });
    });
    await page.route('**/api/users/import', route => {
      calls.push('import');
      return route.fulfill({ json: report(true) });
    });

    await page.goto('/profile');
    await page.getByRole('tab', { name: 'Account' }).click();
    await page.locator('[data-import-file]').setInputFiles({ name: 'indezy-user-data.json', mimeType: 'application/json',
      buffer: Buffer.from('{"clients": []}') });

    const preview = page.locator('[data-import-report]');
    await expect(preview).toContainText('What indezy-user-data.json adds');
    await expect(preview.locator('[data-section="CLIENTS"]')).toContainText('1 new, 0 already here, 1 left out');
    await expect(preview.locator('[data-section="SEASONS"]')).toHaveCount(0);
    await preview.locator('[data-section="CLIENTS"] summary').click();
    await expect(preview).toContainText('left out: City is required');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    expect(calls).toEqual(['preview']);

    await page.locator('[data-import-confirm]').click();
    await expect(preview).toContainText('Imported');
    await expect(preview.locator('[data-section="PROJECTS"]')).toContainText('1 added');
    await expect(page.getByText('Data imported')).toBeVisible();
    expect(calls).toEqual(['preview', 'import']);
  });
}
