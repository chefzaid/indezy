import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const urssaf = { id: 4, type: 'URSSAF_VIGILANCE', label: null, fileName: 'attestation.pdf', contentType: 'application/pdf', sizeBytes: 4,
  issuedOn: '2026-05-16', expiresOn: '2026-11-16', validity: 'EXPIRING', createdAt: '2026-10-27T10:00:00' };
const pack = { id: 9, recipient: 'Capgemini', documentIds: [4], createdAt: '2026-10-27T10:00:00', expiresAt: '2026-11-10T10:00:00Z',
  revokedAt: null, active: true, openCount: 0, lastOpenedAt: null };

for (const width of [1280, 390]) {
  test(`stores a document and shares it through a link (${width}px)`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 900 });
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let documents: unknown[] = [];
    const uploads: string[] = [];
    await page.route('**/api/compliance-documents', route => {
      if (route.request().method() === 'POST') {
        uploads.push(route.request().postData() ?? '');
        documents = [urssaf];
        return route.fulfill({ status: 201, json: urssaf });
      }
      return route.fulfill({ json: documents });
    });
    await page.route('**/api/compliance-packs', route => route.request().method() === 'POST' ?
      route.fulfill({ status: 201, json: { pack, token: 'e2e-pack-token' } }) : route.fulfill({ json: [] }));

    await page.goto('/documents');
    await expect(page.locator('.compliance-page')).toContainText('No document yet');
    await page.locator('[data-add-document]').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('Issued on').fill('2026-05-16');
    await expect(dialog).toContainText('Empty: 6 months after the issue date');
    await dialog.locator('[data-document-file]').setInputFiles({ name: 'attestation.pdf', mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.7') });
    await page.locator('.mat-mdc-dialog-container').evaluate(container =>
      Promise.all(container.getAnimations({ subtree: true }).map(animation => animation.finished)));
    const dialogAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(dialogAudit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-save-document]').click();
    await expect(page.locator('[data-document="4"]')).toContainText('URSSAF vigilance certificate');
    await expect(page.locator('[data-document="4"] [data-document-validity]')).toHaveText('Expires soon');
    expect(uploads[0]).toContain('name="type"\r\n\r\nURSSAF_VIGILANCE');
    expect(uploads[0]).toContain('name="issuedOn"\r\n\r\n2026-05-16');

    await page.locator('[data-share-documents]').click();
    await dialog.getByLabel('For (ESN, recruiter)').fill('Capgemini');
    await dialog.locator('[data-create-link]').click();
    const link = page.locator('[data-shared-link]');
    await expect(link).toContainText('/documents-pack#e2e-pack-token');
    await link.locator('[data-copy-link]').click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toContain('/documents-pack#e2e-pack-token');
    await expect(page.locator('[data-pack="9"]')).toContainText('opened 0 time(s)');
    const pageAudit = await new AxeBuilder({ page }).include('.compliance-page').withTags(WCAG).analyze();
    expect(pageAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('opens shared documents without an account, and says when the link no longer works', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const tokens: (string | undefined)[] = [];
  await page.route('**/api/public/compliance-packs', route => {
    const token = route.request().headers()['x-pack-token'];
    tokens.push(token);
    return token === 'good' ? route.fulfill({ json: { sharedBy: 'Alice Martin', recipient: 'Capgemini', expiresAt: '2026-11-10T10:00:00Z',
      documents: [{ id: 4, type: 'URSSAF_VIGILANCE', label: null, fileName: 'attestation.pdf', issuedOn: '2026-05-16',
        expiresOn: '2026-11-16' }] } }) : route.fulfill({ status: 404, json: { message: 'Link invalid or expired' } });
  });
  await page.route('**/api/public/compliance-packs/documents/4', route =>
    route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' }));

  await page.goto('/documents-pack#good');
  // Without an account the page speaks French until the visitor switches.
  await expect(page.getByRole('heading', { name: 'Documents partagés' })).toBeVisible();
  await expect(page.locator('main')).toContainText('Alice Martin partage ces documents avec Capgemini.');
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Télécharger attestation.pdf' }).click();
  expect((await download).suggestedFilename()).toBe('attestation.pdf');
  const audit = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  expect(page.url()).not.toContain('login');

  await page.getByRole('button', { name: 'English' }).click();
  await expect(page.getByRole('heading', { name: 'Shared documents' })).toBeVisible();
  // A new link is a new page: changing only what follows # would not reload it.
  await page.goto('about:blank');
  await page.goto('/documents-pack#revoked');
  await expect(page.getByRole('heading', { name: 'This link no longer works' })).toBeVisible();
  // Switching the language reloads the page, which reads the link again.
  expect([...new Set(tokens)]).toEqual(['good', 'revoked']);
});
