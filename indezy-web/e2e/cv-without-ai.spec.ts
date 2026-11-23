import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const language of ['en', 'fr']) {
  test(`reads a CV by its headings when AI is not available (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/ai/profile-import', route => route.fulfill({ status: 409, json: { message: 'AI is not available yet' } }));
    const read: string[] = [];
    await page.route('**/api/users/profile-import/cv', route => {
      read.push(route.request().url());
      return route.fulfill({ json: { firstName: 'Workspace', lastName: 'Test', phone: '06 12 34 56 78', city: 'Lyon', position: 'Lead Java',
        company: 'Alice Conseil', bio: null, website: null, linkedin: null, github: null, languages: [], skills: [{ name: 'Kafka', years: 2 }],
        certifications: [], positions: [], education: [] } });
    });
    await page.goto('/profile');
    await page.locator('[data-cv-import]').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-cv-import-file]').setInputFiles({ name: 'cv.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') });
    await expect(dialog.locator('[data-cv-import-row="company"] input')).toBeChecked();
    await expect(page.getByText(language === 'en' ? 'your CV was read by its headings' : "votre CV a été lu d'après ses titres")).toBeVisible();
    expect(read).toHaveLength(1);
  });
}
