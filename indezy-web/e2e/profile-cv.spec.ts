import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

for (const language of ['en', 'fr']) {
  for (const width of [1280, 390]) {
    test(`keeps a CV in the profile and downloads it (${language}, ${width}px)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await mockWorkspaceSession(page, language);
      await mockWorkspaceData(page, language);
      let kept: Record<string, unknown> | null = null;
      await page.route('**/api/users/cv/info', route => kept ? route.fulfill({ json: kept }) : route.fulfill({ status: 204 }));
      await page.route(/\/api\/users\/cv$/, route => {
        if (route.request().method() === 'POST') {
          kept = { fileName: 'cv-alice.pdf', contentType: 'application/pdf', sizeBytes: 8, keptAt: '2026-11-23T10:00:00' };
          return route.fulfill({ json: kept });
        }
        return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
      });

      await page.goto('/profile');
      const card = page.locator('[data-profile-cv]');
      await expect(card.locator('[data-profile-cv-add]')).toBeVisible();
      await card.locator('[data-profile-cv-file]').setInputFiles({ name: 'cv-alice.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7') });
      await expect(card.locator('[data-profile-cv-kept]')).toContainText('cv-alice.pdf');
      const audit = await new AxeBuilder({ page }).include('[data-profile-cv]').withTags(WCAG).analyze();
      expect(audit.violations.map(violation => violation.id)).toEqual([]);
      const download = page.waitForEvent('download');
      await card.locator('[data-profile-cv-download]').click();
      expect((await download).suggestedFilename()).toBe('cv-alice.pdf');
    });
  }
}
