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
    // A 1×1 PNG.
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');
    await page.route('**/api/users/billing/logo', route => {
      identity = { ...identity, logoId: route.request().method() === 'POST' ? 9 : null };
      return route.fulfill({ json: identity });
    });
    await page.route('**/api/users/billing/logos/9', route => route.fulfill({ body: png, contentType: 'image/png' }));
    await page.route('**/api/users/billing/letterhead', route => {
      identity = { ...identity, letterheadId: 6 };
      return route.fulfill({ json: identity });
    });
    await page.route('**/api/users/billing/font', route => {
      identity = { ...identity, font: 'CUSTOM', fontFileId: 4, fontFamily: 'Ma Police' };
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
    await card.locator('[data-billing-logo-file]').setInputFiles({ name: 'logo.png', mimeType: 'image/png', buffer: png });
    await expect(card.locator('[data-billing-logo-preview]')).toBeVisible();
    await card.locator('[data-billing-letterhead-file]').setInputFiles({ name: 'papier.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF') });
    await expect(card.locator('[data-billing-letterhead-open]')).toBeVisible();
    await card.locator('[data-billing-letterhead-top]').fill('45');
    await card.locator('[data-billing-letterhead-bottom]').fill('20');
    // An uploaded font becomes the documents' one.
    await card.locator('[data-billing-font-file]').setInputFiles([{ name: 'MaPolice-Regular.ttf', mimeType: 'font/ttf', buffer: Buffer.from('ttf') },
      { name: 'MaPolice-Bold.ttf', mimeType: 'font/ttf', buffer: Buffer.from('ttf') }]);
    await expect(card.locator('[data-billing-font]')).toContainText('Ma Police (my font)');
    await card.locator('[data-billing-theme]').click();
    await page.getByRole('option', { name: 'Modern' }).click();
    await card.locator('[data-billing-footer]').fill('alice.dev · 06 12 34 56 78');
    await card.locator('[data-billing-quote-terms]').fill('Article 1 — Objet');
    await card.locator('[data-billing-accent]').evaluate((input: HTMLInputElement) => {
      input.value = '#1f4e79';
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await expect(card.locator('[data-billing-accent-row]')).toContainText('#1F4E79');
    // The style previewed before it is saved.
    let previewed: Record<string, unknown> | null = null;
    await page.route('**/api/invoices/style-preview?quote=false**', route => {
      previewed = route.request().postDataJSON();
      return route.fulfill({ body: png, contentType: 'image/png', headers: { 'X-Preview-Pages': '1', 'Access-Control-Expose-Headers': 'X-Preview-Pages' } });
    });
    await card.locator('[data-billing-preview-button]').click();
    await expect(card.locator('[data-billing-preview] img')).toBeVisible();
    expect(previewed).toEqual(expect.objectContaining({ theme: 'MODERN', accentColor: '#1F4E79' }));
    // Quotes in their own layout, previewed on a quote.
    await card.locator('[data-billing-quote-own] input').check();
    await card.locator('[data-billing-quote-theme]').click();
    await page.getByRole('option', { name: 'Classic' }).click();
    let quotePreview = '';
    await page.route('**/api/invoices/style-preview?quote=true**', route => {
      quotePreview = route.request().url();
      previewed = route.request().postDataJSON();
      return route.fulfill({ body: png, contentType: 'image/png', headers: { 'X-Preview-Pages': '2', 'Access-Control-Expose-Headers': 'X-Preview-Pages' } });
    });
    await card.locator('[data-billing-preview-quote]').click();
    await expect.poll(() => quotePreview).toContain('quote=true');
    // Both pages of the quote drawn, then drawn again as the form changes.
    await expect(card.locator('[data-billing-preview] img')).toHaveCount(2);
    expect(previewed).toEqual(expect.objectContaining({ theme: 'MODERN', quoteTheme: 'CLASSIC' }));
    // The notes moved above the mentions, the quotes' signature box left out.
    await card.locator('[data-body-block-up="NOTES"]').click();
    await expect(card.locator('[data-body-block]').nth(1)).toHaveAttribute('data-body-block', 'NOTES');
    await card.locator('[data-body-block-signature] input').uncheck();
    await card.locator('[data-block-framed="NOTES"] input').check();
    await card.locator('[data-block-bold="NOTES"] input').check();
    // The pages' zones, the footer's right one numbering them.
    await card.locator('[data-billing-footer-right]').fill('Page {{page}}/{{pages}}');
    await card.locator('[data-billing-page-header-right]').fill('{{document.title}} {{document.number}}');
    await card.locator('[data-billing-save]').click();

    await expect(card.locator('[data-billing-status]')).toContainText('Ready to issue invoices');
    expect(saved).toEqual([expect.objectContaining({ legalForm: 'MICRO_ENTREPRISE', siret: '55210055400012', letterheadTop: 45, letterheadBottom: 20,
      iban: 'FR7630006000011234567890189', vatExempt: true, quoteTerms: 'Article 1 — Objet', accentColor: '#1F4E79',
      footer: 'alice.dev · 06 12 34 56 78', font: 'CUSTOM', theme: 'MODERN', quoteTheme: 'CLASSIC',
      bodyBlocks: ['LINES', 'NOTES', 'MENTIONS'], footerRight: 'Page {{page}}/{{pages}}', pageHeaderRight: '{{document.title}} {{document.number}}',
      blockStyles: { NOTES: { size: 'SMALL', align: 'LEFT', framed: true, shaded: false, color: 'TEXT', bold: true } } })]);
    const audit = await new AxeBuilder({ page }).include('[data-billing]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
