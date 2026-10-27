import { Page, test } from '@playwright/test';
import { mockDemoReport, mockDemoWorkspace } from './demo-workspace';

// Writes the presentation page's screenshots into src/assets/screens: npx playwright test -c pw.screenshots.config.ts
const OUT = 'src/assets/screens';

async function settle(page: Page): Promise<void> {
  await page.evaluate(() => document.fonts.ready);
  await page.waitForTimeout(800);
}

for (const language of ['fr', 'en'] as const) {
  for (const theme of ['light', 'dark'] as const) {
    test(`screens (${language}, ${theme})`, async ({ page, browser }) => {
      test.setTimeout(120_000);
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.emulateMedia({ colorScheme: theme });
      await mockDemoWorkspace(page, language, theme);
      const shot = (name: string) => page.screenshot({ path: `${OUT}/${name}-${language}-${theme}.jpg`, type: 'jpeg', quality: 80 });

      await page.addInitScript(() => localStorage.setItem('indezy-dashboard-view', 'kanban'));
      await page.goto('/dashboard');
      await page.locator('.kanban-card, [data-kanban-card], .project-card').first().waitFor();
      await settle(page);
      await shot('kanban');

      await page.addInitScript(() => localStorage.setItem('indezy-dashboard-view', 'overview'));
      await page.goto('/dashboard');
      await page.getByText(/Banque Méridienne/).first().waitFor();
      await settle(page);
      await shot('dashboard');

      await page.goto('/offers');
      await page.getByText('Développeur Java Spring Boot senior').first().waitFor();
      await settle(page);
      await shot('offers');

      // Opened without an account: a context of its own, without the demo session.
      const visitor = await browser.newContext({ viewport: { width: 1280, height: 800 }, colorScheme: theme });
      const report = await visitor.newPage();
      await mockDemoReport(report, language, theme);
      await report.goto('/activity-report#demo-token');
      await report.getByText('Lead Java Kafka').first().waitFor();
      await settle(report);
      await report.screenshot({ path: `${OUT}/report-${language}-${theme}.jpg`, type: 'jpeg', quality: 80 });
      await visitor.close();
    });
  }
}
