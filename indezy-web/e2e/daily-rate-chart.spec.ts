import { expect, test } from '@playwright/test';

for (const language of ['en', 'fr']) {
  test(`shows exact daily rates and updates them when choosing a season (${language})`, async ({ page }) => {
    const errors: string[] = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.addInitScript(lang => {
      const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
      localStorage.setItem('indezy_token', `header.${payload}.browser-test`);
      localStorage.setItem('indezy_user', JSON.stringify({
        id: 1, email: 'chart@example.test', firstName: 'Chart', lastName: 'Test'
      }));
      localStorage.setItem('indezy-lang', lang);
      localStorage.setItem('indezy-season', 'all');
      localStorage.setItem('indezy-dashboard-view', 'overview');
    }, language);

    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light' } }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [{
      id: 9, freelanceId: 1, name: 'Autumn search', startDate: '2026-09-01', active: true
    }] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => {
      const scoped = new URL(route.request().url()).searchParams.has('seasonId');
      return route.fulfill({ json: {
        totalProjects: scoped ? 1 : 4, averageDailyRate: scoped ? 650 : 600,
        totalEstimatedRevenue: 0, forecastRevenue: 0, activeProjects: scoped ? 1 : 4,
        wonProjects: 0, lostProjects: 0, totalBenchDays: 0, benchPeriods: 0, estimatedBenchCost: 0,
        projectsByStatus: { CONTACT: scoped ? 1 : 4 }, projectsByWorkMode: { REMOTE: scoped ? 1 : 4 },
        dailyRateRanges: scoped ? [{ label: '650', count: 1 }] : [
          { label: '550', count: 1 }, { label: '600', count: 2 }, { label: '650', count: 1 }
        ]
      } });
    });

    await page.goto('/dashboard');
    const title = language === 'en' ? 'Daily Rate Distribution' : 'Répartition des TJM';
    const chart = page.getByRole('img', { name: new RegExp(title, 'i') });
    await expect(chart).toBeVisible();
    const currency = new Intl.NumberFormat(language === 'en' ? 'en-US' : 'fr-FR', {
      style: 'currency', currency: 'EUR', maximumFractionDigits: 0
    });
    await expect(chart).toHaveAttribute('aria-label', new RegExp(`${currency.format(600)}: 2`));
    await expect(chart).toHaveAttribute('aria-label', new RegExp(`${currency.format(550)}: 1`));
    await chart.screenshot({ path: test.info().outputPath('daily-rate-chart.png') });

    await page.getByRole('combobox').click();
    await page.getByRole('option', { name: /Autumn search/ }).click();
    await expect(chart).toHaveAttribute('aria-label', new RegExp(`${currency.format(650)}: 1`));
    await expect(chart).not.toHaveAttribute('aria-label', /550|600/);
    expect(errors).toEqual([]);
  });
}
