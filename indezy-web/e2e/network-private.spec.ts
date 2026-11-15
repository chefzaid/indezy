import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };
const card = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: null, stack: [],
  city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`asks to follow a private card, then withdraws the request (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    let memberPage = { card: card('leo_m', 'Léo'), followers: 4, following: 2, sharedSkills: [], followed: false, followsYou: false,
      own: false, muted: false, blocked: false, privateCard: true, requested: false };
    await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: memberPage }));
    await page.route('**/api/network/members/leo_m/follow', route => {
      memberPage = { ...memberPage, requested: route.request().method() === 'PUT' };
      return route.fulfill({ json: memberPage });
    });
    await page.route('**/api/network/members/leo_m/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));

    await page.goto('/m/leo_m');
    await expect(page.locator('[data-posts-private]')).toContainText(en ? 'only the followers they approve' : 'seuls les abonnés');
    await expect(page.locator('[data-follow]')).toContainText(en ? 'Ask to follow' : 'Demander à suivre');
    await page.locator('[data-follow]').click();
    await expect(page.locator('[data-follow]')).toContainText(en ? 'Request sent' : 'Demande envoyée');
    await expect(page.locator('[data-follow]')).toHaveAttribute('aria-pressed', 'true');
    const audit = await new AxeBuilder({ page }).include('app-member-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.locator('[data-follow]').click();
    await expect(page.locator('[data-follow]')).toContainText(en ? 'Ask to follow' : 'Demander à suivre');
  });
}

test('turns the card private from the profile and approves a request to follow', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Camille', lastName: 'Durand',
    email: 'workspace@example.test' } }));
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language: 'en', inactivityDays: 14 } }));
  await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
  const membership = { joined: true, handle: 'camille', fields, availability: { mode: 'AUTO', date: null }, card: card('camille', 'Camille'),
    privateCard: false };
  await page.route('**/api/network/membership', route => route.fulfill({ json: membership }));
  await page.route('**/api/network/restrictions', route => route.fulfill({ json: { muted: [], blocked: [] } }));
  await page.route('**/api/network/notifications/settings', route => route.fulfill({ json: { off: [] } }));
  await page.route('**/api/network/handles/*', route => route.fulfill({ json: { handle: 'camille', available: true } }));
  const calls: string[] = [];
  await page.route('**/api/network/membership/private?**', route => {
    calls.push(new URL(route.request().url()).searchParams.get('private')!);
    return route.fulfill({ json: { ...membership, privateCard: true } });
  });
  await page.route('**/api/network/follow-requests', route => route.fulfill({ json: [card('leo_m', 'Léo')] }));
  await page.route('**/api/network/follow-requests/leo_m', route => {
    calls.push(`${route.request().method()} leo_m`);
    return route.fulfill({ json: [] });
  });

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Network' }).click();
  const section = page.locator('[data-network-private]');
  await section.locator('[data-private-toggle]').getByRole('switch').click();
  await expect(section.locator('[data-private-toggle]').getByRole('switch')).toHaveAttribute('aria-checked', 'true');
  await expect(section).toContainText('Requests to follow (1)');
  await section.locator('[data-follow-request="leo_m"] [data-request-approve]').click();
  await expect(section.locator('[data-follow-request]')).toHaveCount(0);
  expect(calls).toEqual(['true', 'PUT leo_m']);
});
