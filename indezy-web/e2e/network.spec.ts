import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`joins the member network with a handle and a card of its own choosing (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Camille', lastName: 'Durand',
      email: 'workspace@example.test' } }));
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language, inactivityDays: 14 } }));
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
    let membership: Record<string, unknown> = { joined: false, handle: 'camille', fields, availability: { mode: 'AUTO', date: null },
      card: null };
    const calls: unknown[] = [];
    await page.route('**/api/network/membership', route => {
      const method = route.request().method();
      if (method === 'PUT') {
        const body = route.request().postDataJSON();
        calls.push(body);
        membership = { joined: true, handle: body.handle, fields: body.fields, availability: body.availability, card: { handle: body.handle,
          firstName: 'Camille', lastName: 'Durand', avatar: null, headline: 'Architecte Java', stack: ['Java', 'Kafka'],
          city: body.fields.location ? 'Lyon' : null, remote: body.fields.remote, availability: body.availability.mode,
          availabilityDate: body.availability.date, languages: [] } };
      } else if (method === 'DELETE') {
        calls.push('leave');
        membership = { ...membership, joined: false, card: null };
        return route.fulfill({ status: 204 });
      }
      return route.fulfill({ json: membership });
    });
    await page.route('**/api/network/handles/*', route => {
      const handle = decodeURIComponent(route.request().url().split('/').at(-1)!);
      return route.fulfill({ json: { handle, available: handle !== 'taken' } });
    });

    await page.goto('/profile');
    await page.getByRole('tab', { name: en ? 'Network' : 'Réseau' }).click();
    const tab = page.locator('[data-profile-network]');
    await expect(tab.locator('[data-network-handle]')).toHaveValue('camille');
    await tab.locator('[data-network-handle]').fill('taken');
    await expect(tab).toContainText(en ? 'Another member holds this handle.' : 'Un autre membre a déjà cet identifiant.');
    await tab.locator('[data-network-handle]').fill('camille_d');
    await tab.locator('[data-network-field="location"]').getByRole('switch').click();
    await tab.locator('[data-network-field="remote"]').getByRole('switch').click();
    await tab.locator('[data-network-availability]').click();
    await page.getByRole('option', { name: en ? 'On a mission' : 'En mission' }).click();
    await tab.locator('[data-network-availability-date] input').fill('2027-03-31');
    await tab.locator('[data-network-save]').click();

    const card = page.locator('[data-member-card="camille_d"]');
    await expect(card).toContainText('Camille Durand');
    await expect(card).toContainText(en ? 'Remote' : 'À distance');
    await expect(card.locator('[data-card-availability]')).toContainText(en ? 'On a mission until March 2027' : 'En mission jusqu');
    await expect(card).not.toContainText('Lyon');
    await expect(tab.locator('[data-network-joined]')).toContainText('@camille_d');
    const audit = await new AxeBuilder({ page }).include('[data-profile-network]').include('[data-network-preview]')
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await tab.locator('[data-network-leave]').click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Leave the network' : 'Quitter le réseau' }).click();
    await expect(page.locator('[data-network-preview]')).toHaveCount(0);
    await expect(tab.locator('[data-network-handle]')).toHaveValue('camille_d');
    expect(calls).toEqual([{ handle: 'camille_d', fields: { ...fields, location: false, remote: true },
      availability: { mode: 'ON_MISSION', date: '2027-03-31' } }, 'leave']);
  });
}

test('keeps the network out of the profile until an administrator opens it', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Camille', lastName: 'Durand',
    email: 'workspace@example.test' } }));
  await page.goto('/profile');
  await expect(page.getByRole('tab', { name: 'Billing' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Network' })).toHaveCount(0);
});

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`follows a member from their page, seeing the skills both share (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const leo = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: 'Data engineer', stack: ['Kafka', 'Spark'],
      city: 'Nantes', remote: true, availability: 'AVAILABLE', availabilityDate: '2026-12-02', languages: ['Français'] };
    let memberPage = { card: leo, followers: 4, following: 2, sharedSkills: ['Kafka'], followed: false, followsYou: true, own: false };
    await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: memberPage }));
    await page.route('**/api/network/members/leo_m/follow', route => {
      memberPage = { ...memberPage, followed: route.request().method() === 'PUT', followers: route.request().method() === 'PUT' ? 5 : 4 };
      return route.fulfill({ json: memberPage });
    });
    await page.route('**/api/network/members/leo_m/followers', route => route.fulfill({ json: [
      { ...leo, handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard', headline: 'Scrum master' }] }));

    await page.goto('/m/leo_m');
    const card = page.locator('[data-member-card="leo_m"]');
    await expect(card).toContainText('Léo Martin');
    await expect(card).toContainText('@leo_m');
    await expect(page.locator('[data-follows-you]')).toBeVisible();
    await expect(page.locator('[data-shared-skills]')).toContainText('Kafka');
    await page.locator('[data-follow]').click();
    await expect(page.locator('[data-follow]')).toContainText(en ? 'Unfollow' : 'Ne plus suivre');
    await expect(page.locator('[data-followers]')).toContainText(en ? '5 followers' : '5 abonnés');
    await page.locator('[data-followers]').click();
    await expect(page.locator('[data-member-list="followers"]')).toContainText('Inès Bernard');
    await expect(page.locator('[data-member-list="followers"] a')).toHaveAttribute('href', '/m/ines_b');
    const audit = await new AxeBuilder({ page }).include('app-member-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('asks a visitor who is not a member to join before seeing members', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ status: 409, json: { message: 'Join the network to see its members' } }));
  await page.goto('/m/leo_m');
  await expect(page.locator('[data-member-problem="notMember"]')).toContainText('Join the network to see its members.');
  await expect(page.getByRole('link', { name: 'Join from Profile → Network' })).toHaveAttribute('href', '/profile');
});
