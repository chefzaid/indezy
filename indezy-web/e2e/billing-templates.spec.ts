import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const PLACEHOLDERS = ['document.title', 'document.number', 'document.issueDate', 'document.dueDate', 'document.period', 'document.validUntil',
  'document.draft', 'seller.name', 'seller.details', 'seller.address', 'seller.siret', 'seller.vatNumber', 'seller.iban', 'seller.bic',
  'client.name', 'client.details', 'client.address', 'client.siren', 'client.vatNumber', '#lines', 'line.description', 'line.quantity',
  'line.unitPrice', 'line.vatRate', 'line.amount', '/lines', 'totals.excludingVat', 'totals.vat', 'totals.toPay', 'mentions', 'notes',
  'acceptance', 'logo', '#vatExempt', '^vatExempt', '/vatExempt'];

for (const language of ['en', 'fr']) {
  for (const width of [1280, 390]) {
    test(`keeps a Word template for the invoices (${language}, ${width}px)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await mockWorkspaceSession(page, language);
      await mockWorkspaceData(page, language);
      await page.route('**/api/users/billing/templates', route => route.fulfill({ json: [] }));
      await page.route('**/api/users/billing/templates/placeholders', route => route.fulfill({ json: PLACEHOLDERS }));
      await page.route('**/api/users/billing/templates/INVOICE', route => route.fulfill({ json: { kind: 'INVOICE', fileName: 'modele.docx',
        uploadedAt: '2026-11-23T10:00:00', found: ['invoice.number', '#lines', 'line.amount', '/lines'], unknown: ['iban.inconnu'],
        missing: ['mentions'] } }));

      await page.goto('/profile');
      await page.getByRole('tab', { name: language === 'en' ? 'Billing' : 'Facturation' }).click();
      const section = page.locator('[data-billing-templates]');
      await expect(section).toContainText(language === 'en' ? 'Word templates' : 'Modèles Word');
      await expect(section).toContainText('{{invoice.number}}');
      await section.locator('[data-billing-template-file="INVOICE"]').setInputFiles({ name: 'modele.docx',
        mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', buffer: Buffer.from('PK') });
      await expect(section.locator('[data-billing-template="INVOICE"]')).toContainText('modele.docx');
      await expect(section.locator('[data-billing-template-unknown]')).toContainText('{{iban.inconnu}}');
      await expect(section.locator('[data-billing-template-missing]')).toContainText('mentions');
      await page.route('**/api/invoices/template-preview', route => route.fulfill({ body: 'PK', contentType:
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }));
      const preview = page.waitForEvent('download');
      await section.locator('[data-billing-template-preview="INVOICE"]').click();
      expect((await preview).suggestedFilename()).toBe('apercu-modele-facture.docx');
      await section.locator('[data-billing-placeholders] summary').click();
      await expect(section.locator('[data-billing-placeholders] li')).toHaveCount(PLACEHOLDERS.length);
      const audit = await new AxeBuilder({ page }).include('[data-billing-templates]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      expect(audit.violations.map(violation => violation.id)).toEqual([]);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    });

    test(`reads the placeholder guide in the help center (${language}, ${width}px)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(language === 'en' ? '/help/document-templates' : '/help/document-templates');
      await page.evaluate(lang => localStorage.setItem('indezy-lang', lang), language);
      await page.reload();
      await expect(page.locator('h1')).toContainText(language === 'en' ? 'Document templates' : 'Modèles de documents');
      await expect(page.locator('table').first()).toContainText('{{document.number}}');
      const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      expect(audit.violations.map(violation => violation.id)).toEqual([]);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    });
  }
}
