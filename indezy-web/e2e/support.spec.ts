import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`writes to support with the technical context and reads the answers (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    const sent: Record<string, unknown>[] = [];
    await page.route('**/api/support/requests', route => {
      if (route.request().method() === 'POST') {
        sent.push(route.request().postDataJSON());
        return route.fulfill({ status: 201, json: { id: 2, kind: 'PROBLEM', subject: 'PDF', description: 'Blank', context: null,
          status: 'OPEN', answer: null, createdAt: '2026-11-22T12:00:00', answeredAt: null, attachments: [] } });
      }
      return route.fulfill({ json: [{ id: 1, kind: 'QUESTION', subject: en ? 'Export' : 'Export', description: '?', context: null,
        status: 'CLOSED', answer: en ? 'From Profile → Account.' : 'Depuis Profil → Compte.', createdAt: '2026-11-20T10:00:00',
        answeredAt: '2026-11-20T11:00:00Z', attachments: [{ id: 5, requestId: 1, fileName: 'export.pdf', contentType: 'application/pdf',
          sizeBytes: 900 }] }] });
    });
    const uploaded: string[] = [];
    await page.route('**/api/support/requests/2/attachments', route => {
      uploaded.push(route.request().postData() ?? '');
      return route.fulfill({ status: 201, json: { id: 6, requestId: 2, fileName: 'capture.jpg', contentType: 'image/jpeg', sizeBytes: 3 } });
    });
    await page.route('**/api/support/requests/1/attachments/5', route => route.fulfill({ body: '%PDF-1.4', contentType: 'application/pdf' }));

    await page.goto('/support?page=/invoices');
    await expect(page.locator('[data-support-answer]')).toContainText(en ? 'From Profile → Account.' : 'Depuis Profil → Compte.');
    await expect(page.locator('[data-support-context]')).toContainText('/invoices');
    const audit = await new AxeBuilder({ page }).include('.support-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('[data-support-subject]').fill('PDF');
    await page.locator('[data-support-description]').fill('Blank');
    await page.locator('[data-support-files]').setInputFiles([{ name: 'capture.png', mimeType: 'image/png', buffer: Buffer.from('png') }]);
    await expect(page.locator('[data-support-file="capture.png"]')).toBeVisible();
    await page.locator('[data-support-send]').click();
    await expect(page.locator('[data-support-request="2"] [data-support-attachment="capture.jpg"]')).toBeVisible();
    expect(uploaded[0]).toContain('capture.png');
    await expect(page.locator('[data-support-file]')).toHaveCount(0);
    const download = page.waitForEvent('download');
    await page.locator('[data-support-attachment="export.pdf"]').click();
    expect((await download).suggestedFilename()).toBe('export.pdf');
    expect(sent[0]).toEqual(expect.objectContaining({ kind: 'PROBLEM', subject: 'PDF', description: 'Blank' }));
    expect(String(sent[0]['context'])).toContain('/invoices');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
