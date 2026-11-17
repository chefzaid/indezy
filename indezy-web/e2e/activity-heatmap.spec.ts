import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession, themeByWidth } from './helpers/workspace';

/** A local ISO day, so many days before today. */
function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

// A year of prospection, busier lately; three days ago, two opportunities and an interview.
const activity = Array.from({ length: 365 }, (_, ago) => ago).filter(ago => ago % 3 === 0 || ago < 40 && ago % 2 === 0)
  .map(ago => ({ date: daysAgo(ago), count: ago === 3 ? 3 : 1 + ((ago * 7) % 9), opportunities: 1, interviews: 0, items: [] as unknown[] }));
const busyDay = activity.find(day => day.date === daysAgo(3))!;
Object.assign(busyDay, { opportunities: 2, interviews: 1, items: [
  { kind: 'OPPORTUNITY', label: 'Architecte Kafka', client: 'Banque Martin', projectId: 42 },
  { kind: 'OPPORTUNITY', label: 'Lead Java', client: null, projectId: 43 },
  { kind: 'INTERVIEW', label: 'Entretien technique', client: 'Banque Martin', projectId: 42 }] });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`shows a year of prospection as a GitHub-style grid, each day telling what happened (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: { ...emptyDashboardStats, totalProjects: 3,
      activeProjects: 2, activityHeatmap: activity } }));
    await page.route('**/api/projects/42', route => route.fulfill({ json: { id: 42, role: 'Architecte Kafka', status: 'INTERVIEW',
      freelanceId: 1 } }));

    await page.goto('/dashboard');
    const heatmap = page.locator('[data-heatmap]');
    await heatmap.scrollIntoViewIfNeeded();
    const total = activity.reduce((sum, day) => sum + day.count, 0);
    await expect(heatmap.locator('[data-heatmap-summary]')).toContainText(en ? `${total} activities in the last year`
      : `${total} activités sur les 12 derniers mois`);
    // Months above, weekdays beside.
    await expect(heatmap.locator('.month').first()).toBeVisible();
    await expect(heatmap.locator('.weekday').first()).toHaveText(en ? 'Mon' : 'lun.');
    // The latest weeks show first.
    const today = heatmap.locator(`[data-heatmap-day="${daysAgo(0)}"]`);
    await expect(today).toBeInViewport();

    // Hovering a day grows it and tells what happened.
    const busy = heatmap.locator(`[data-heatmap-day="${daysAgo(3)}"]`);
    await busy.hover();
    await expect(busy).toHaveCSS('transform', /matrix\(1\.9/);
    const card = page.locator('[data-heatmap-card]');
    await expect(card).toContainText(en ? '3 activities' : '3 activités');
    await expect(card).toContainText(en ? '2 opportunities · 1 interview' : '2 opportunités · 1 entretien');
    await expect(card).toContainText('Architecte Kafka');
    await expect(card).toContainText('Banque Martin');
    await page.mouse.move(0, 0);
    await expect(card).toHaveCount(0);

    // The keyboard moves between days; Enter keeps the card open, its opportunities a click away.
    await busy.click();
    await expect(busy).toHaveAttribute('aria-expanded', 'true');
    const audit = await new AxeBuilder({ page }).include('[data-heatmap]').include('[data-heatmap-card]')
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.keyboard.press('Escape');
    await expect(busy).toBeFocused();
    await expect(busy).toHaveAttribute('aria-expanded', 'false');
    await page.keyboard.press('ArrowDown');
    await expect(heatmap.locator(`[data-heatmap-day="${daysAgo(2)}"]`)).toBeFocused();
    await page.keyboard.press('ArrowUp');
    await page.keyboard.press('Enter');
    await card.locator('[data-heatmap-item="42"]').first().click();
    await expect(page).toHaveURL(/\/projects\/42$/);
  });
}
