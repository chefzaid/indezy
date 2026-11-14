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
          availabilityDate: body.availability.date, languages: [], verified: false } };
      } else if (method === 'DELETE') {
        calls.push('leave');
        membership = { ...membership, joined: false, card: null };
        return route.fulfill({ status: 204 });
      }
      return route.fulfill({ json: membership });
    });
    await page.route('**/api/network/restrictions', route => route.fulfill({ json: { muted: [], blocked: [] } }));
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
      city: 'Nantes', remote: true, availability: 'AVAILABLE', availabilityDate: '2026-12-02', languages: ['Français'], verified: true };
    let memberPage = { card: leo, followers: 4, following: 2, sharedSkills: ['Kafka'], followed: false, followsYou: true, own: false,
      muted: false, blocked: false };
    await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: memberPage }));
    await page.route('**/api/network/members/leo_m/follow', route => {
      memberPage = { ...memberPage, followed: route.request().method() === 'PUT', followers: route.request().method() === 'PUT' ? 5 : 4 };
      return route.fulfill({ json: memberPage });
    });
    await page.route('**/api/network/members/leo_m/posts?**', route => route.fulfill({ json: { posts: [{ id: 7, text: 'Retour sur #Kafka',
      tags: ['kafka'], createdAt: '2026-11-10T09:00:00', own: false, likes: 0, liked: false, comments: 0, commentsClosed: false, author: leo }], page: 0, more: false } }));
    await page.route('**/api/network/members/leo_m/followers', route => route.fulfill({ json: [
      { ...leo, handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard', headline: 'Scrum master' }] }));

    await page.goto('/m/leo_m');
    const card = page.locator('[data-member-card="leo_m"]');
    await expect(card).toContainText('Léo Martin');
    await expect(card).toContainText('@leo_m');
    await expect(card.locator('[data-card-verified]')).toBeVisible();
    await expect(page.locator('[data-follows-you]')).toBeVisible();
    await expect(page.locator('[data-shared-skills]')).toContainText('Kafka');
    await expect(page.locator('[data-member-posts] [data-post="7"]')).toContainText('Retour sur #Kafka');
    await page.locator('[data-follow]').click();
    await expect(page.locator('[data-follow]')).toContainText(en ? 'Unfollow' : 'Ne plus suivre');
    await expect(page.locator('[data-followers]')).toContainText(en ? '5 followers' : '5 abonnés');
    await page.locator('[data-followers]').click();
    await expect(page.locator('[data-member-list="followers"]')).toContainText('Inès Bernard');
    await expect(page.locator('[data-member-list="followers"] a')).toHaveAttribute('href', '/m/ines_b');
    const audit = await new AxeBuilder({ page }).include('app-member-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    // Blocking ends the follow and replaces Follow by a note; unblocking brings Follow back.
    await page.route('**/api/network/members/leo_m/block', route => {
      const blocked = route.request().method() === 'PUT';
      memberPage = { ...memberPage, blocked, followed: false, followsYou: false };
      return route.fulfill({ json: memberPage });
    });
    await page.locator('[data-member-more]').click();
    await page.locator('[data-block]').click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Block' : 'Bloquer' }).click();
    await expect(page.locator('[data-blocked]')).toBeVisible();
    await expect(page.locator('[data-follow]')).toHaveCount(0);
    await page.locator('[data-member-more]').click();
    await page.locator('[data-block]').click();
    await expect(page.locator('[data-follow]')).toContainText(en ? 'Follow' : 'Suivre');
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

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`finds members by skill and availability in the directory (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const member = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: 'Data engineer',
      stack: ['Kafka'], city: 'Lyon', remote: true, availability: 'LISTENING', availabilityDate: null, languages: [], verified: false });
    const asked: string[] = [];
    await page.route('**/api/network/members?**', route => {
      const query = new URL(route.request().url()).searchParams;
      asked.push(query.toString());
      const members = query.get('skill') === 'Kafka' ? [member('leo_m', 'Léo')] : [member('leo_m', 'Léo'), member('ines_b', 'Inès')];
      return route.fulfill({ json: { members, total: members.length, page: 0, size: 20 } });
    });

    await page.goto('/network/members');
    await expect(page.locator('[data-directory-count]')).toContainText(en ? 'Members found: 2' : 'Membres trouvés : 2');
    await page.locator('[data-directory-skill]').fill('Kafka');
    await page.locator('[data-directory-availability]').click();
    await page.getByRole('option', { name: en ? 'Open to offers' : "À l'écoute" }).click();
    await page.locator('[data-directory-search]').click();
    await expect(page.locator('[data-directory-count]')).toContainText(en ? 'Members found: 1' : 'Membres trouvés : 1');
    await expect(page.locator('[data-directory-member="leo_m"]')).toHaveAttribute('href', '/m/leo_m');
    expect(asked.at(-1)).toBe('page=0&skill=Kafka&availability=LISTENING');
    const audit = await new AxeBuilder({ page }).include('app-member-directory').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`writes a post to the home feed, mentioning a member (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const author = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Durand', avatar: null, headline: null, stack: [],
      city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
    const posted: unknown[] = [];
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [{ id: 1, text: 'Bonjour à tous', tags: [],
      createdAt: '2026-11-13T09:00:00', own: false, likes: 1, liked: false, comments: 0, commentsClosed: false, author: author('leo_m', 'Léo') }], page: 0, more: false } }));
    await page.route('**/api/network/posts', route => {
      const body = route.request().postDataJSON();
      posted.push(body);
      return route.fulfill({ status: 201, json: { id: 2, text: body.text, tags: ['kafka'], createdAt: '2026-11-14T10:00:00', own: true,
        likes: 0, liked: false, comments: 0, commentsClosed: false, author: author('camille', 'Camille') } });
    });

    await page.goto('/network');
    await expect(page.locator('[data-post="1"]')).toContainText('Bonjour à tous');
    await page.route('**/api/network/posts/1/like', route => route.fulfill({ json: { id: 1, text: 'Bonjour à tous', tags: [],
      createdAt: '2026-11-13T09:00:00', own: false, likes: 2, liked: true, comments: 0, commentsClosed: false, author: author('leo_m', 'Léo') } }));
    await page.locator('[data-post="1"] [data-like]').click();
    await expect(page.locator('[data-post="1"] [data-like]')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('[data-post="1"] [data-likes]')).toContainText(en ? 'Likes: 2' : "J'aime : 2");
    let thread: Record<string, unknown>[] = [];
    await page.route('**/api/network/posts/1/comments', route => {
      if (route.request().method() === 'POST') {
        thread = [{ id: 11, author: author('camille', 'Camille'), text: route.request().postDataJSON().text, createdAt: '2026-11-14T10:05:00',
          own: true, hidden: false, pinned: false, replies: [] }];
        return route.fulfill({ status: 201, json: thread[0] });
      }
      return route.fulfill({ json: thread });
    });
    await page.locator('[data-post="1"] [data-show-comments]').click();
    await page.locator('[data-post="1"] [data-comment-text]').fill('Bienvenue !');
    await page.locator('[data-post="1"] [data-comment-send]').click();
    await expect(page.locator('[data-post="1"] [data-comment="11"]')).toContainText('Bienvenue !');
    await expect(page.locator('[data-post="1"] [data-show-comments]')).toContainText(en ? 'Comments: 1' : 'Commentaires : 1');
    await expect(page.locator('[data-publish]')).toBeDisabled();
    await page.locator('[data-composer-text]').fill('Merci @leo_m pour le retour sur #Kafka');
    await page.locator('[data-publish]').click();
    const mine = page.locator('[data-post="2"]');
    await expect(mine).toContainText('Merci');
    await expect(mine.locator('.mention')).toHaveAttribute('href', '/m/leo_m');
    await expect(page.locator('[data-post]').first()).toHaveAttribute('data-post', '2');
    expect(posted).toEqual([{ text: 'Merci @leo_m pour le retour sur #Kafka' }]);
    await expect(page.locator('[data-feed-members]')).toHaveAttribute('href', '/network/members');
    const audit = await new AxeBuilder({ page }).include('app-network-feed').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
