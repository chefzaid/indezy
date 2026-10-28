import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const steps = [
  { id: 1, title: 'Technical test', status: 'PLANNED', date: '2026-10-20T14:00:00', projectId: 42, projectRole: 'Java developer' },
  { id: 2, title: 'Call', status: 'VALIDATED', date: '2026-10-20T09:30:00', projectId: 43, projectRole: 'Ops' },
  { id: 3, title: 'Client interview', status: 'PLANNED', date: '2026-11-03T10:00:00', projectId: 42, projectRole: 'Java developer' }
];

for (const width of [1280, 390]) {
  test(`shows a month of interview steps and opens one (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
    await page.route('**/api/interview-steps/by-freelance/1*', route => route.fulfill({ json: steps }));
    await page.route('**/api/projects/42', route => route.fulfill({ json: { id: 42, role: 'Java developer', status: 'INTERVIEW', dailyRate: 650 } }));
    await page.route('**/api/projects/42/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/interview-steps/by-project/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/bookings', route => route.fulfill({ json: [] }));
    await page.route('**/api/users/booking-page', route => route.fulfill({ json: { slug: null, settings: { enabled: false, title: '',
      durationMinutes: 30, bufferMinutes: 15, minNoticeHours: 24, maxDaysAhead: 21, windows: [] } } }));

    await page.goto('/calendar?month=2026-10');
    await expect(page.getByText('October 2026')).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByRole('gridcell', { name: /^Tuesday, October 20, 2026: 2 step\(s\)/ }).click();
    const detail = page.locator('.day-detail');
    await expect(detail).toContainText('09:30');
    await expect(detail).toContainText('Technical test');
    await page.locator('.month-grid').screenshot({ path: test.info().outputPath(`calendar-${width}.png`) });

    await page.getByRole('button', { name: 'Next month' }).click();
    await expect(page).toHaveURL(/month=2026-11/);
    await expect(page.getByText('November 2026')).toBeVisible();
    await page.getByRole('gridcell', { name: /^Tuesday, November 3, 2026: 1 step\(s\)/ }).click();
    await page.getByRole('link', { name: /Client interview/ }).click();
    await expect(page).toHaveURL(/\/projects\/42$/);
  });

  test(`lists the planned steps and booked calls to come (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
    // Only planned steps still to come are listed: not a past one, nor one already validated.
    await page.route('**/api/interview-steps/by-freelance/1*', route => route.fulfill({ json: [
      { id: 4, title: 'Final round', status: 'PLANNED', date: '2099-03-02T15:00:00', projectId: 42, projectRole: 'Java developer' },
      { id: 5, title: 'Old call', status: 'PLANNED', date: '2020-03-02T15:00:00', projectId: 42, projectRole: 'Java developer' },
      { id: 6, title: 'Debrief', status: 'VALIDATED', date: '2099-03-03T15:00:00', projectId: 42, projectRole: 'Java developer' }] }));
    await page.route('**/api/bookings', route => route.fulfill({ json: [{ id: 8, name: 'Claire', startsAt: '2099-03-01T09:00:00Z',
      endsAt: '2099-03-01T09:30:00Z', email: 'claire@esn.example', company: 'ESN', message: null, cancelledAt: null }] }));
    await page.route('**/api/users/booking-page', route => route.fulfill({ json: { slug: null, settings: { enabled: false, title: '',
      durationMinutes: 30, bufferMinutes: 15, minNoticeHours: 24, maxDaysAhead: 21, windows: [] } } }));

    await page.goto('/calendar');
    await page.locator('[data-calendar-view="upcoming"]').click();
    await expect(page).toHaveURL(/view=upcoming/);
    const rows = page.locator('[data-calendar-upcoming] .detail-step');
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText('Call with Claire');
    await expect(rows.nth(1)).toContainText('Final round');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.locator('[data-calendar-view="month"]').click();
    await expect(page.locator('.month-grid')).toBeVisible();
  });
}
