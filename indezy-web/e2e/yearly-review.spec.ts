import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const review = (year: number) => ({
  year,
  name: 'Alice Martin',
  missions: year === 2026 ? [
    { projectId: 42, role: 'Java Lead', clientName: 'Acme', dailyRate: 600, start: '2026-01-05', end: '2026-06-30', days: 110,
      reported: true, revenue: 66000 },
    { projectId: 43, role: 'Kafka Developer', clientName: 'Globex', dailyRate: 700, start: '2026-09-01', days: 40.5,
      reported: false, revenue: 28350 }
  ] : [],
  totals: year === 2026 ? { revenue: 94350, days: 150.5, averageDailyRate: 627, missions: 2 } : { revenue: 0, days: 0, missions: 0 },
  previousYear: year === 2026 ? { revenue: 0, days: 0, missions: 0 } : { revenue: 0, days: 0, missions: 0 },
  pipeline: year === 2026 ?
    { opened: 9, won: 2, lost: 3, conversionRate: 40, lostReasons: [{ reason: 'RATE_TOO_LOW', count: 2 }, { reason: 'OTHER', count: 1 }] } :
    { opened: 0, won: 0, lost: 0, lostReasons: [] },
  topSkills: year === 2026 ? [{ skill: 'Java', count: 6 }, { skill: 'Kafka', count: 3 }] : [],
  network: year === 2026 ? { clients: 3, esns: 2, contacts: 7, exchanges: 15 } : { clients: 0, esns: 0, contacts: 0, exchanges: 0 }
});

for (const width of [1280, 390]) {
  test(`reviews a year, moves to the one before and downloads the PDF (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.clock.setFixedTime(new Date('2026-10-27T10:00:00'));
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    await page.route('**/api/projects/review/1?*', route =>
      route.fulfill({ json: review(Number(new URL(route.request().url()).searchParams.get('year'))) }));
    let pdfLanguage: string | null = null;
    await page.route('**/api/projects/review/1/pdf?*', route => {
      pdfLanguage = new URL(route.request().url()).searchParams.get('language');
      return route.fulfill({ body: '%PDF-1.4', contentType: 'application/pdf' });
    });

    await page.goto('/dashboard');
    if (width < 600) {
      await page.getByRole('button', { name: 'Toggle navigation menu' }).click();
    }
    await page.getByRole('link', { name: 'Year in review' }).click();
    await expect(page).toHaveURL(/\/review$/);
    await expect(page.getByRole('heading', { name: 'Year in review' })).toBeVisible();
    await expect(page.locator('[data-year]')).toHaveText('2026');
    await expect(page.locator('[data-figure-revenue]')).toHaveText('94,350 €');
    await expect(page.getByText('Nothing in 2025')).toBeVisible();
    const missions = page.locator('[data-missions] tbody tr');
    await expect(missions).toHaveCount(2);
    await expect(missions.nth(1)).toContainText('ongoing');
    await expect(page.locator('[data-estimate-note]')).toBeVisible();
    await expect(page.locator('[data-pipeline]')).toContainText('40 %');
    await expect(page.getByText('Rate too low')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Next year' })).toBeDisabled();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await expect(page.locator('app-dashboard')).toHaveCount(0);
    await page.screenshot({ path: test.info().outputPath(`yearly-review-${width}.png`), fullPage: true });

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Download PDF' }).click();
    expect((await download).suggestedFilename()).toBe('indezy-year-in-review-2026.pdf');
    expect(pdfLanguage).toBe('EN');

    await page.getByRole('button', { name: 'Previous year' }).click();
    await expect(page.locator('[data-year]')).toHaveText('2025');
    await expect(page.locator('[data-review-empty]')).toContainText('Nothing recorded in 2025 yet.');
    await expect(page.getByText('No mission won covers this year.')).toBeVisible();

    await page.getByRole('button', { name: 'Next year' }).click();
    await missions.first().getByRole('link', { name: 'Java Lead' }).click();
    await expect(page).toHaveURL(/\/projects\/42$/);
  });
}
