import { Page } from '@playwright/test';

/** Browser-only session for tests whose API responses are intercepted. */
export async function mockWorkspaceSession(page: Page, language: string): Promise<void> {
  await page.addInitScript(lang => {
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
    localStorage.setItem('indezy_token', `header.${payload}.browser-test`);
    localStorage.setItem('indezy_user', JSON.stringify({
      id: 1, email: 'workspace@example.test', firstName: 'Workspace', lastName: 'Test'
    }));
    localStorage.setItem('indezy-lang', lang);
    localStorage.setItem('indezy-season', 'all');
    localStorage.setItem('indezy-dashboard-view', 'overview');
  }, language);
}

export const emptyDashboardStats = {
  totalProjects: 0, averageDailyRate: 0, totalEstimatedRevenue: 0, forecastRevenue: 0,
  activeProjects: 0, wonProjects: 0, lostProjects: 0, totalBenchDays: 0, benchPeriods: 0,
  estimatedBenchCost: 0, projectsByStatus: {}, projectsByWorkMode: {}, dailyRateRanges: []
};
