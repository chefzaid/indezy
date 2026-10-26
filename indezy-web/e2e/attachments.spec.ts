import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const PDF = '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj ' +
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF';

test('attaches a job description, shows it in the page and deletes it', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
  await page.route('**/api/projects/42', route => route.fulfill({ json: { id: 42, role: 'Java developer', status: 'CONTACT', dailyRate: 650 } }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
  let files: Record<string, unknown>[] = [];
  let uploadedName = '';
  await page.route('**/api/projects/42/attachments', async route => {
    if (route.request().method() === 'POST') {
      uploadedName = /filename="([^"]+)"/.exec(route.request().postData() ?? '')?.[1] ?? '';
      const file = { id: 5, fileName: uploadedName, contentType: 'application/pdf', sizeBytes: PDF.length,
        createdAt: '2026-10-26T10:00:00', inline: true };
      files = [file];
      await route.fulfill({ status: 201, json: file });
    } else {
      await route.fulfill({ json: files });
    }
  });
  await page.route('**/api/projects/attachments/5/content', route => route.fulfill({
    body: PDF, headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'inline; filename="Job brief.pdf"' }
  }));
  let deleted = false;
  await page.route('**/api/projects/attachments/5', async route => {
    deleted = route.request().method() === 'DELETE';
    await route.fulfill({ status: 204 });
  });

  await page.goto('/projects/42');
  await expect(page.getByText('No files yet')).toBeVisible();
  const chooser = page.waitForEvent('filechooser');
  await page.getByRole('button', { name: 'Attach a file' }).click();
  await (await chooser).setFiles({ name: 'Job brief.pdf', mimeType: 'application/pdf', buffer: Buffer.from(PDF) });
  await expect(page.getByText('File attached')).toBeVisible();
  expect(uploadedName).toBe('Job brief.pdf');

  await page.getByRole('button', { name: 'Show "Job brief.pdf"' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('heading', { name: 'Job brief.pdf' })).toBeVisible();
  await expect(dialog.locator('iframe')).toHaveAttribute('src', /^blob:/);
  await dialog.getByRole('button', { name: 'Close' }).click();

  await page.getByRole('button', { name: 'Delete "Job brief.pdf"' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /Delete|Confirm/ }).click();
  await expect(page.getByText('File deleted')).toBeVisible();
  expect(deleted).toBe(true);
});
