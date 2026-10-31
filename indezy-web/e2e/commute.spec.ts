import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const commute = (projectId: number, extra: Record<string, unknown>) => ({ projectId, projectRole: 'Mission', origin: 'Home',
  destination: 'Client', travelMode: 'DRIVING', remote: false, onsiteDaysPerMonth: 20, ...extra });

for (const width of [1280, 390]) {
  test(`sorts the missions by what their commute costs a month (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    const projects = [
      { id: 1, role: 'Java developer', dailyRate: 600, status: 'CONTACT', workMode: 'ONSITE' },
      { id: 2, role: 'Kafka engineer', dailyRate: 700, status: 'CONTACT', workMode: 'REMOTE' },
      { id: 3, role: 'Go developer', dailyRate: 450, status: 'CONTACT', workMode: 'HYBRID' },
      { id: 4, role: 'Data engineer', dailyRate: 500, status: 'CONTACT', workMode: 'ONSITE' },
      { id: 5, role: 'SRE', dailyRate: 620, status: 'CONTACT', workMode: 'ONSITE' }
    ];
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: projects }));
    const modes: string[] = [];
    await page.route('**/api/commute/projects/1*', route => {
      modes.push(new URL(route.request().url()).searchParams.get('travelMode')!);
      return route.fulfill({ json: [
        { project: projects[1], commute: commute(2, { remote: true, onsiteDaysPerMonth: 0, monthlySeconds: 0 }) },
        { project: projects[2], commute: commute(3, { onsiteDaysPerMonth: 8, durationInSeconds: 3600, durationText: '1 hour',
          distanceText: '60 km', monthlySeconds: 57600 }) },
        { project: projects[0], commute: commute(1, { durationInSeconds: 1800, durationText: '30 mins', distanceText: '20 km',
          monthlySeconds: 72000 }) },
        { project: projects[3], commute: commute(4, { unavailable: 'NO_CLIENT_ADDRESS' }) },
        { project: projects[4], commute: commute(5, { durationInSeconds: 3000, rushDurationInSeconds: 6300, distanceInMeters: 56000,
          estimated: true, monthlySeconds: 252000 }) }
      ] });
    });

    await page.goto('/projects');
    const titles = page.locator('.project-title');
    await expect(titles).toHaveCount(5);
    if (width < 760) {
      await page.getByRole('button', { name: /Filters/ }).first().click();
    }
    await page.getByRole('combobox', { name: 'Sort by' }).click();
    await page.getByRole('option', { name: 'Commute time' }).click();
    if (width < 760) {
      await page.keyboard.press('Escape');
    }
    await expect(titles).toHaveText(['Kafka engineer', 'Go developer', 'Java developer', 'SRE', 'Data engineer']);
    // Issou to Montrouge by car: some 50 minutes off-peak, 1 h 45 at rush hour, which the month counts.
    await expect(page.locator('[data-commute="5"]')).toContainText('≈ 50 min');
    await expect(page.locator('[data-commute="5"]')).toContainText('≈ 1 h 45 at rush hour');
    await expect(page.locator('[data-commute="5"]')).toContainText('estimate');
    await expect(page.locator('[data-commute="2"]')).toContainText('Remote: no commute');
    await expect(page.locator('[data-commute="3"]')).toContainText('1 hour');
    await expect(page.locator('[data-commute="3"]')).toContainText('(60 km)');
    await expect(page.locator('[data-commute="3"]')).toContainText('· 16 h a month there and back at rush hour (8 days on site)');
    await expect(page.locator('[data-commute="1"]')).toContainText('20 h a month there and back at rush hour (20 days on site)');
    await expect(page.locator('[data-commute="4"]')).toContainText('The client has no address.');
    expect(modes).toEqual(['DRIVING']);
    const audit = await new AxeBuilder({ page }).include('.project-list-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
