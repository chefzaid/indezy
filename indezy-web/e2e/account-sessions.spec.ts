import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const laptop = { id: 1, method: 'PASSWORD', device: 'Firefox on Windows', createdAt: '2026-10-20T08:00:00',
  lastSeenAt: '2026-10-27T09:00:00', expiresAt: '2026-10-28T08:00:00', current: true };
const phone = { ...laptop, id: 2, method: 'KEYCLOAK', device: 'Chrome on Android', current: false };
const tablet = { ...laptop, id: 3, device: 'Safari on iPad', current: false };
const activity = { events: [
  { id: 6, type: 'SIGN_IN_REFUSED', detail: 'PASSWORD', device: 'Chrome on Linux', occurredAt: '2026-10-27T08:00:00' },
  { id: 5, type: 'SIGNED_IN', detail: 'PASSWORD', device: 'Firefox on Windows', occurredAt: '2026-10-20T08:00:00' }
], total: 2, page: 0, size: 20 };

for (const width of [1280, 390]) {
  test(`signs out another session, then everywhere else, and reads the account activity (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let sessions = [laptop, phone, tablet];
    const calls: string[] = [];
    await page.route('**/api/users/sessions', route => route.fulfill({ json: sessions }));
    await page.route('**/api/users/sessions/2', route => {
      calls.push(`${route.request().method()} 2`);
      sessions = sessions.filter(session => session.id !== 2);
      return route.fulfill({ status: 204 });
    });
    await page.route('**/api/users/sessions/end-others', route => {
      calls.push('end others');
      return route.fulfill({ json: { count: 1 } });
    });
    await page.route('**/api/users/activity?*', route => route.fulfill({ json: activity }));

    await page.goto('/profile');
    await page.getByRole('tab', { name: 'Security' }).click();
    const list = page.locator('[data-sessions]');
    await expect(list.locator('[data-session="1"]')).toContainText('This browser');
    await expect(page.locator('[data-activity]')).toContainText('Sign-in refused: wrong password');
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    const audit = await new AxeBuilder({ page }).include('[data-sessions]').include('[data-activity]')
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);

    await list.getByRole('button', { name: 'Sign out Chrome on Android' }).click();
    await expect(list.locator('[data-session="2"]')).toHaveCount(0);
    await list.locator('[data-end-others]').click();
    await page.getByRole('dialog').getByRole('button', { name: 'Sign out everywhere else' }).click();
    await expect(page.getByText('Signed out of 1 other sessions')).toBeVisible();
    await expect(list.locator('[data-session]')).toHaveCount(1);
    expect(calls).toEqual(['DELETE 2', 'end others']);
  });
}

test('signing out ends the session on the server', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const ended: (string | null)[] = [];
  await page.route('**/api/users/sessions/current', route => {
    ended.push(route.request().headers()['authorization'] ?? null);
    return route.fulfill({ status: 204 });
  });

  await page.goto('/dashboard');
  await page.locator('.user-button').click();
  await page.getByRole('menuitem', { name: 'Sign out' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect.poll(() => ended).toEqual([expect.stringMatching(/^Bearer header\./)]);
});

test('creates an API token shown once, then revokes it', async ({ page, context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write']);
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  const token = { id: 4, name: 'Zapier', tokenStart: 'idz_AbCdEfGh', createdAt: '2026-10-27T09:00:00', lastUsedAt: null,
    expiresAt: '2027-01-25T09:00:00' };
  const requests: unknown[] = [];
  await page.route('**/api/users/api-tokens', route => {
    if (route.request().method() === 'POST') {
      requests.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: { token, secret: 'idz_AbCdEfGhsecret' } });
    }
    return route.fulfill({ json: [] });
  });
  await page.route('**/api/users/api-tokens/4', route => {
    requests.push(route.request().method());
    return route.fulfill({ status: 204 });
  });

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Security' }).click();
  const card = page.locator('[data-api-tokens]');
  await card.getByLabel('Token name').fill('Zapier');
  await card.getByRole('button', { name: 'Create token' }).click();
  await expect(card.locator('[data-token-secret]')).toHaveText('idz_AbCdEfGhsecret');
  const audit = await new AxeBuilder({ page }).include('[data-api-tokens]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await card.getByRole('button', { name: 'Copy token' }).click();
  expect(await page.evaluate(() => navigator.clipboard.readText())).toBe('idz_AbCdEfGhsecret');
  await card.getByRole('button', { name: 'Done' }).click();
  await expect(card.locator('[data-token-secret]')).toHaveCount(0);

  await card.getByRole('button', { name: 'Revoke Zapier' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Revoke' }).click();
  await expect(card.locator('[data-token]')).toHaveCount(0);
  expect(requests).toEqual([{ name: 'Zapier', expiresInDays: 90 }, 'DELETE']);
});
