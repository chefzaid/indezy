import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`shares a declined opportunity to the network, its client left out (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
    await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
    const project = { id: 42, role: 'Lead Java', status: 'WITHDRAWN', refusedBy: 'FREELANCE', clientId: 3, clientName: 'Acme', dailyRate: 700,
      techStack: 'Java, Kafka', remoteDaysPerMonth: 9, workAddress: 'Lyon', startDate: '2027-01-04', freelanceId: 1 };
    await page.route('**/api/projects/42', route => route.fulfill({ json: project }));
    await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
    await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));

    await page.goto('/projects/42');
    await page.locator('[data-share-to-network]').click();
    await expect(page).toHaveURL(/\/network$/);
    await expect(page.locator('[data-composer-text]')).toHaveValue(en ? 'A mission I am not taking, for whoever it suits.' :
      'Une mission que je ne prends pas, pour qui elle conviendra.');
    await expect(page.locator('[data-fact="role"]')).toHaveValue('Lead Java');
    await expect(page.locator('[data-fact="rateMax"]')).toHaveValue('700');
    await expect(page.locator('[data-fact="remoteDays"]')).toHaveValue('2');
    await expect(page.locator('[data-fact="stack"]')).toHaveValue('Java, Kafka');
    await expect(page.locator('[data-fact="client"]')).toHaveValue('');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('shares a signed mission as a milestone, its client left out', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en' } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
  await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.route('**/api/projects/42', route => route.fulfill({ json: { id: 42, role: 'Lead Java', status: 'WON', clientId: 3,
    clientName: 'Acme', dailyRate: 700, freelanceId: 1 } }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));

  await page.goto('/projects/42');
  await page.locator('[data-share-to-network]').click();
  await expect(page.locator('[data-composer-text]')).toHaveValue('Mission signed: Lead Java. On to the next one!');
  await expect(page.locator('[data-composer-text]')).not.toHaveValue(/Acme/);
});
