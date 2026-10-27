import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const shown = {
  firstName: 'Workspace', lastName: 'Test', position: 'Java lead', city: 'Lyon', bio: 'Java and Kafka since 2012.',
  skills: [{ name: 'Java', level: 'EXPERT', years: 12 }, { name: 'Kafka' }], languages: ['French', 'English'],
  website: 'https://workspace.example', availableNow: false, availableFrom: '2026-11-02'
};

test('turns the public profile on from the profile page', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const saved: unknown[] = [];
  await page.route('**/api/users/public-profile', route => {
    if (route.request().method() === 'PUT') {
      saved.push(route.request().postDataJSON());
      return route.fulfill({ json: route.request().postDataJSON() });
    }
    return route.fulfill({ json: { enabled: false, slug: null } });
  });

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Public profile' }).click();
  await expect(page.locator('[data-public-slug]')).toHaveValue('workspace-test');
  await page.getByRole('switch', { name: 'Show my public profile' }).click();
  await page.locator('[data-public-save]').click();

  await expect(page.locator('[data-public-link] a')).toHaveText(/\/p\/workspace-test$/);
  expect(saved).toEqual([{ enabled: true, slug: 'workspace-test' }]);
});

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`shows a public profile to a visitor without an account (${language}, ${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
    await page.route('**/api/public/profiles/workspace-test', route => route.fulfill({ json: shown }));
    await page.route('**/api/public/profiles/nobody', route => route.fulfill({ status: 404, json: { status: 404 } }));

    await page.goto('/p/workspace-test');
    await expect(page.getByRole('heading', { name: 'Workspace Test' })).toBeVisible();
    await expect(page.getByText('Java lead')).toBeVisible();
    await expect(page.locator('[data-availability]')).toContainText(language === 'en' ? 'Available from' : 'Disponible à partir du');
    await expect(page.locator('[data-skills]')).toContainText('Java');
    await expect(page).toHaveURL(/\/p\/workspace-test$/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: test.info().outputPath(`public-profile-${width}.png`), fullPage: true });

    await page.goto('/p/nobody');
    await expect(page.locator('[data-profile-not-found]')).toBeVisible();
  });
}
