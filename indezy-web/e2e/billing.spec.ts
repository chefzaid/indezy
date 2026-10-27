import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const width of [1280, 390]) {
  test(`fills in the billing identity on the profile (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let identity: Record<string, unknown> = { legalName: 'Workspace Test', legalForm: null, siret: null, vatNumber: null,
      vatExempt: true, vatRate: 20, shareCapital: null, registration: null, address: null, iban: null, bic: null,
      paymentTermDays: 30, invoicePrefix: 'F', complete: false };
    const saved: Record<string, unknown>[] = [];
    await page.route('**/api/users/billing', route => {
      if (route.request().method() === 'PUT') {
        saved.push(route.request().postDataJSON());
        identity = { ...route.request().postDataJSON(), complete: true };
      }
      return route.fulfill({ json: identity });
    });

    await page.goto('/profile');
    await page.getByRole('tab', { name: 'Billing' }).click();
    const card = page.locator('[data-billing]');
    await expect(card.locator('[data-billing-status]')).toContainText('Fill in the SIRET');
    await card.getByLabel('Legal form').click();
    await page.getByRole('option', { name: 'Micro-entreprise (EI)' }).click();
    await card.getByLabel('SIRET').fill('552 100 554 00012');
    await card.getByLabel('Billing address').fill('18 rue du Sentier\n75002 Paris');
    await card.getByLabel('IBAN').fill('FR76 3000 6000 0112 3456 7890 188');
    await card.locator('[data-billing-save]').click();
    await expect(card.getByText('This IBAN is not valid')).toBeVisible();
    await card.getByLabel('IBAN').fill('FR76 3000 6000 0112 3456 7890 189');
    await card.locator('[data-billing-save]').click();

    await expect(card.locator('[data-billing-status]')).toContainText('Ready to issue invoices');
    expect(saved).toEqual([expect.objectContaining({ legalForm: 'MICRO_ENTREPRISE', siret: '55210055400012',
      iban: 'FR7630006000011234567890189', vatExempt: true })]);
    const audit = await new AxeBuilder({ page }).include('[data-billing]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
