import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

/** A blank tile: the tests never ask OpenStreetMap. */
const TILE = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ip1sAAAAASUVORK5CYII=', 'base64');

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`maps the opportunities around home, coloured by stage (${language})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    await page.route('https://tile.openstreetmap.org/**', route => route.fulfill({ body: TILE, contentType: 'image/png' }));
    await page.route('**/api/commute/map/1', route => route.fulfill({ json: {
      home: { latitude: 48.869, longitude: 2.331 },
      pins: [
        { projectId: 42, role: 'Java developer', clientName: 'Acme', status: 'INTERVIEW', place: 'Lyon', latitude: 45.764, longitude: 4.836 },
        { projectId: 43, role: 'Data engineer', clientName: 'Acme', status: 'WON', place: '92400 Courbevoie', latitude: 48.898, longitude: 2.257 }
      ],
      unplaced: 1 } }));

    await page.goto('/projects');
    await page.locator('[data-open-map]').click();
    await expect(page).toHaveURL(/\/projects\/map$/);
    const map = page.locator('[data-map]');
    await expect(map.locator('path.leaflet-interactive')).toHaveCount(3);
    await expect(page.locator('[data-map-summary]')).toContainText(language === 'en' ? '2 opportunity(ies) placed' : '2 opportunité(s) placée(s)');
    await expect(page.locator('.leaflet-control-attribution')).toContainText('OpenStreetMap');
    await page.screenshot({ path: test.info().outputPath('map.png') });

    await map.locator('path.pin-INTERVIEW').click();
    await expect(page.locator('.pin-popup')).toContainText('Acme · ');
    const audit = await new AxeBuilder({ page }).include('.opportunity-map-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('.pin-popup a').click();
    await expect(page).toHaveURL(/\/projects\/42$/);
  });
}
