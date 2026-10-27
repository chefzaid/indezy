import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const width of [1280, 390]) {
  test(`hands a contact their data, then erases them on request (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    const contact = { id: 3, firstName: 'Claire', lastName: 'Martin', clientId: 1, clientName: 'Acme', freelanceId: 1, legalBasis: 'CONSENT' };
    let erased = false;
    await page.route('**/api/contacts/3', route => route.fulfill({ json: contact }));
    await page.route('**/api/contacts/3/interactions', route => route.fulfill({ json: { interactions: [], asked: 0, answered: 0 } }));
    await page.route('**/api/contacts/3/relations', route => route.fulfill({ json: [] }));
    await page.route('**/api/contacts/3/personal-data', route => route.fulfill({ json: { firstName: 'Claire' },
      headers: { 'Content-Disposition': 'attachment; filename="contact-3.json"' } }));
    await page.route('**/api/contacts/3/erase', route => {
      erased = route.request().method() === 'POST';
      return route.fulfill({ status: 204 });
    });

    await page.goto('/contacts/3');
    const card = page.locator('[data-contact-privacy]');
    await expect(card.locator('[data-legal-basis]')).toHaveText('Consent');
    const audit = await new AxeBuilder({ page }).include('[data-contact-privacy]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    const download = page.waitForEvent('download');
    await card.getByRole('button', { name: 'Download their data' }).click();
    expect((await download).suggestedFilename()).toBe('contact-3.json');

    await card.getByRole('button', { name: 'Erase on their request' }).click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText('Claire Martin, their exchanges and links are erased for good.');
    await dialog.getByRole('button', { name: 'Erase on their request' }).click();
    await expect(page).toHaveURL(/\/contacts$/);
    expect(erased).toBe(true);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
