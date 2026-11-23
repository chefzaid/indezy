import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession, themeByWidth } from './helpers/workspace';

const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`joins the member network with a handle and a card of its own choosing (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
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
    let messaging = { readReceipts: true, typing: true };
    await page.route('**/api/network/messages/settings', route => {
      if (route.request().method() === 'PUT') {
        messaging = { ...messaging, ...route.request().postDataJSON() };
        calls.push(route.request().postDataJSON());
      }
      return route.fulfill({ json: messaging });
    });
    let notices: Record<string, unknown> = { off: [], emailed: [] };
    await page.route('**/api/network/notifications/settings', route => {
      if (route.request().method() === 'PUT') {
        notices = route.request().postDataJSON();
        calls.push(notices);
      }
      return route.fulfill({ json: notices });
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
    await page.locator('[data-notice-channel="LIKE"]').getByRole('radio', { name: en ? 'Email too' : 'E-mail aussi' }).click();
    await expect(page.locator('[data-notice-channel="LIKE"]').getByRole('radio', { name: en ? 'Email too' : 'E-mail aussi' }))
      .toHaveAttribute('aria-checked', 'true');
    const typing = page.locator('[data-messaging="typing"]').getByRole('switch');
    await typing.click();
    await expect(typing).toHaveAttribute('aria-checked', 'false');
    const audit = await new AxeBuilder({ page }).include('[data-profile-network]').include('[data-network-notices]').include('[data-network-messaging]')
      .include('[data-network-preview]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await tab.locator('[data-network-leave]').click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Leave the network' : 'Quitter le réseau' }).click();
    await expect(page.locator('[data-network-preview]')).toHaveCount(0);
    await expect(tab.locator('[data-network-handle]')).toHaveValue('camille_d');
    expect(calls).toEqual([{ handle: 'camille_d', fields: { ...fields, location: false, remote: true },
      availability: { mode: 'ON_MISSION', date: '2027-03-31' } }, { off: [], emailed: ['LIKE'] }, { typing: false }, 'leave']);
  });
}

test('keeps the network out of the menu and the profile while an administrator turns it off', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: false } } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Camille', lastName: 'Durand',
    email: 'workspace@example.test' } }));
  await page.goto('/profile');
  await expect(page.getByRole('tab', { name: 'Billing' })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Network' })).toHaveCount(0);
  await expect(page.locator('a[href="/network"]')).toHaveCount(0);
});

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`follows a member from their page, seeing the skills both share (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
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
      tags: ['kafka'], createdAt: '2026-11-10T09:00:00', own: false, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, author: leo }], page: 0, more: false } }));
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
    await themeByWidth(page, language, width);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const member = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: 'Data engineer',
      stack: ['Kafka'], city: 'Lyon', remote: true, availability: 'LISTENING', availabilityDate: null, languages: [], verified: false });
    const asked: string[] = [];
    await page.route('**/api/network/members?**', route => {
      const query = new URL(route.request().url()).searchParams;
      asked.push(query.toString());
      const members = query.get('skill') === 'Kafka' ? [member('leo_m', 'Léo')] : [member('leo_m', 'Léo'), member('ines_b', 'Inès')];
      return route.fulfill({ json: { members, total: members.length, page: 0, size: 20, followed: [], requested: [], privateCards: [] } });
    });

    await page.goto('/network/members');
    await expect(page.locator('[data-directory-count]')).toContainText(en ? 'Members found: 2' : 'Membres trouvés : 2');
    await page.locator('[data-directory-skill]').fill('Kafka');
    await page.locator('[data-directory-search]').click();
    await expect(page.locator('[data-directory-count]')).toContainText(en ? 'Members found: 1' : 'Membres trouvés : 1');
    await page.locator('[data-directory-availability-option="LISTENING"]').click();
    await expect(page.locator('[data-directory-availability-option="LISTENING"]')).toHaveAttribute('aria-pressed', 'true');
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
    await themeByWidth(page, language, width);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const author = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Durand', avatar: null, headline: null, stack: [],
      city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
    const posted: unknown[] = [];
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [{ id: 1, text: 'Bonjour à tous', tags: [],
      createdAt: '2026-11-13T09:00:00', own: false, likes: 1, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, author: author('leo_m', 'Léo') }], page: 0, more: false } }));
    await page.route('**/api/network/posts', route => {
      const body = route.request().postDataJSON();
      posted.push(body);
      const files = (body.files ?? []).map((ref: { id: number; alt?: string }) => ({ ...uploads[ref.id], alt: ref.alt ?? null }));
      return route.fulfill({ status: 201, json: { id: 2, text: body.text, tags: ['kafka'], createdAt: '2026-11-14T10:00:00', own: true,
        likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, author: author('camille', 'Camille'), files } });
    });
    // An image and a PDF added to the post, served through their signed addresses.
    const uploads: Record<number, Record<string, unknown>> = {
      21: { id: 21, kind: 'IMAGE', name: 'tableau.jpg', contentType: 'image/jpeg', size: 52_000, alt: null, width: 1200, height: 800, pages: null,
        url: '/public/network-files/21/full?e=1&s=a', previewUrl: null },
      22: { id: 22, kind: 'DOCUMENT', name: 'retour.pdf', contentType: 'application/pdf', size: 840_000, alt: null, width: null, height: null,
        pages: 4, url: '/public/network-files/22/full?e=1&s=b', previewUrl: '/public/network-files/22/preview?e=1&s=c' } };
    let uploaded = 20;
    await page.route('**/api/network/files', route => {
      uploaded++;
      return route.fulfill({ status: 201, json: { file: uploads[uploaded], confidential: uploaded === 22 ? [{ found: 'Banque Martin',
        kind: 'CLIENT', city: 'Paris' }] : [] } });
    });
    const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');
    await page.route('**/api/public/network-files/**', route => route.fulfill({ status: 200, contentType: 'image/png', body: pixel }));

    await page.route('**/api/network/suggestions', route => route.fulfill({ json: [{ card: author('ines_b', 'Inès'), reason: 'SAME_STACK',
      followedBy: 0, followedByName: null, sharedSkills: ['Kafka'] }] }));
    await page.route('**/api/network/benchmark/skills', route => route.fulfill({ json: { skills: [{ skill: 'Java', freelancers: 7 }],
      threshold: 5, sharing: false } }));
    await page.route(/\/api\/network\/benchmark\?/, route => route.fulfill({ json: { skill: 'Java', seniority: null, region: null,
      freelancers: 7, threshold: 5, median: 650, low: 600, high: 720, sharing: false } }));
    await page.route('**/api/network/esn-scorecards', route => route.fulfill({ json: { threshold: 5, sharing: true, companies: [
      { company: 'Conseil Exemple', freelancers: 6, threshold: 5, offerRate: 40, processDays: 21, marginLow: 18, marginHigh: 25,
        answerDays: 3, paymentDays: 45, sharing: true }] } }));
    await page.goto('/network');
    await expect(page.locator('[data-suggestion="ines_b"]')).toContainText(en ? 'Also on Kafka' : 'Aussi sur Kafka');
    await expect(page.locator('[data-benchmark-median]')).toContainText(en ? '650 € a day' : '650 € par jour');
    if (await page.locator('[data-rate-benchmark]').isVisible()) {
      const benchmarkAudit = await new AxeBuilder({ page }).include('[data-rate-benchmark]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      expect(benchmarkAudit.violations.map(violation => violation.id)).toEqual([]);
    }
    // The ESN scorecards beside it: the ESN named, its figures from six freelancers' records.
    if (await page.locator('[data-esn-scorecards]').isVisible()) {
      await expect(page.locator('[data-esn-scorecard="Conseil Exemple"] [data-esn-margin]')).toContainText(en ? '18 to 25 %' : '18 à 25 %');
      const esnAudit = await new AxeBuilder({ page }).include('[data-esn-scorecards]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      expect(esnAudit.violations.map(violation => violation.id)).toEqual([]);
    }
    await expect(page.locator('[data-post="1"]')).toContainText('Bonjour à tous');
    await page.route('**/api/network/posts/1/like**', route => route.fulfill({ json: { id: 1, text: 'Bonjour à tous', tags: [],
      createdAt: '2026-11-13T09:00:00', own: false, likes: 2, liked: true, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, author: author('leo_m', 'Léo'),
      myReaction: 'LIKE', reactions: [{ reaction: 'LIKE', count: 2 }] } }));
    await page.locator('[data-post="1"] [data-like]').click();
    await expect(page.locator('[data-post="1"] [data-like]')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('[data-post="1"] [data-likes]')).toContainText(en ? 'Reactions: 2' : 'Réactions : 2');
    let thread: Record<string, unknown>[] = [];
    await page.route('**/api/network/posts/1/comments', route => {
      if (route.request().method() === 'POST') {
        thread = [{ id: 11, author: author('camille', 'Camille'), text: route.request().postDataJSON().text, createdAt: '2026-11-14T10:05:00',
          own: true, hidden: false, pinned: false, likes: 0, liked: false, replies: [] }];
        return route.fulfill({ status: 201, json: thread[0] });
      }
      return route.fulfill({ json: thread });
    });
    await page.locator('[data-post="1"] [data-show-comments]').click();
    await page.locator('[data-post="1"] [data-comment-text]').fill('Bienvenue !');
    await page.locator('[data-post="1"] [data-comment-send]').click();
    await expect(page.locator('[data-post="1"] [data-comment="11"]')).toContainText('Bienvenue !');
    await expect(page.locator('[data-post="1"] [data-comment-count]')).toContainText(en ? 'Comments: 1' : 'Commentaires : 1');
    await page.route('**/api/network/comments/11/like', route => route.fulfill({ json: { ...thread[0], likes: 1, liked: true } }));
    await page.locator('[data-comment="11"] [data-comment-like]').click();
    await expect(page.locator('[data-comment="11"] [data-comment-like]')).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator('[data-comment="11"] [data-comment-like]')).toContainText('1');
    if (width < 600) {
      await page.locator('[data-composer-open]').click();
    }
    await expect(page.locator('[data-publish]')).toBeDisabled();
    await page.locator('[data-composer-text]').fill('Merci @leo_m pour le retour sur #Kafka');
    await page.locator('[data-pick-images-input]').setInputFiles({ name: 'tableau.jpg', mimeType: 'image/jpeg', buffer: pixel });
    await page.locator('[data-pick-documents-input]').setInputFiles({ name: 'retour.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7') });
    await expect(page.locator('[data-picked-confidential]')).toContainText(en ? 'This document names Banque Martin' : 'Ce document cite Banque Martin');
    await page.locator('[data-picked-keep]').click();
    // Each image is described before the post goes.
    await expect(page.locator('[data-publish]')).toBeDisabled();
    await page.locator('[data-picked-alt]').fill('Le tableau blanc de la rétro');
    await page.locator('[data-publish]').click();
    const mine = page.locator('[data-post="2"]');
    await expect(mine).toContainText('Merci');
    await expect(mine.locator('.mention')).toHaveAttribute('href', '/m/leo_m');
    await expect(page.locator('[data-post]').first()).toHaveAttribute('data-post', '2');
    expect(posted).toEqual([{ text: 'Merci @leo_m pour le retour sur #Kafka', kind: 'POST', details: null, poll: null,
      files: [{ id: 21, alt: 'Le tableau blanc de la rétro' }, { id: 22 }] }]);
    await expect(mine.locator('[data-files-images] img')).toHaveAttribute('alt', 'Le tableau blanc de la rétro');
    await expect(mine.locator('[data-files-documents]')).toContainText('retour.pdf');
    await expect(mine.locator('[data-files-documents]')).toContainText(en ? 'PDF · 820 KB · 4 pages' : 'PDF · 820 KB · 4 pages');
    await expect(page.locator('[data-feed-members]')).toHaveAttribute('href', '/network/members');
    const audit = await new AxeBuilder({ page }).include('app-network-feed').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('finds the saved posts by tag on the saved tab of the reader\'s page', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  const leo = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false,
    availability: null, availabilityDate: null, languages: [], verified: false };
  const post = (id: number, text: string, tags: string[]) => ({ id, text, tags, createdAt: '2026-11-13T09:00:00', own: false, likes: 0,
    liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: true, repostOf: null, reposts: 0, repostsOff: false, author: leo });
  await page.route('**/api/network/bookmarks?**', route => {
    const tag = new URL(route.request().url()).searchParams.get('tag');
    const posts = tag === 'kafka' ? [post(1, 'Retour #Kafka', ['kafka'])] : [post(2, 'Atelier #Spark', ['spark']), post(1, 'Retour #Kafka', ['kafka'])];
    return route.fulfill({ json: { page: { posts, page: 0, more: false }, tags: ['kafka', 'spark'] } });
  });
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
  await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: { card: { ...leo, handle: 'camille',
    firstName: 'Camille', lastName: 'Durand' }, followers: 0, following: 0, sharedSkills: [], followed: false, followsYou: false, own: true,
    muted: false, blocked: false, privateCard: false, requested: false } }));
  await page.route('**/api/network/members/camille/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
  // The saved posts are a tab of the reader's own page now; the old address leads there.
  await page.goto('/network/saved');
  await expect(page).toHaveURL(/\/m\/camille\?tab=saved$/);
  await expect(page.locator('[data-tab-saved]')).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('[data-post]')).toHaveCount(2);
  await page.locator('[data-saved-tag="kafka"]').click();
  await expect(page.locator('[data-post]')).toHaveCount(1);
  await expect(page.locator('[data-post="1"]')).toContainText('Retour');
  const audit = await new AxeBuilder({ page }).include('app-saved-posts').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`follows a tag from its page, reached from a post (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false,
      features: { SOCIAL_NETWORK: true, OFFER_FEED: true } } }));
    await page.route('**/api/offers?**', route => route.fulfill({ json: { content: [{ id: 41, site: 'FREEWORK', siteName: 'Free-Work',
      url: 'https://example.test/41', title: 'Lead Kafka', city: 'Lyon', rateMin: 650, rateMax: 700 }], page: 0, size: 5, totalElements: 1,
      totalPages: 1 } }));
    const leo = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: ['Kafka'], city: null,
      remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
    const post = { id: 3, text: 'Retour sur #Kafka', tags: ['kafka'], createdAt: '2026-11-13T09:00:00', own: false, likes: 0, liked: false,
      comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, author: leo };
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [post], page: 0, more: false } }));
    let followed = false;
    await page.route('**/api/network/tags/kafka?**', route => route.fulfill({ json: { tag: 'kafka', followed,
      posts: { posts: [post], page: 0, more: false }, members: [leo] } }));
    await page.route('**/api/network/tags/kafka/follow', route => {
      followed = route.request().method() === 'PUT';
      return route.fulfill({ json: followed ? ['kafka'] : [] });
    });

    await page.goto('/network');
    await page.locator('[data-post="3"] .tag').click();
    await expect(page).toHaveURL(/\/network\/tags\/kafka$/);
    await expect(page.locator('[data-tag-members]')).toContainText('Léo Martin');
    await expect(page.locator('[data-tag-mission="41"]')).toContainText('650–700 €');
    await expect(page.locator('[data-tag-missions-all]')).toHaveAttribute('href', /skills=kafka/);
    await expect(page.locator('[data-tag-posts] [data-post="3"]')).toBeVisible();
    await page.locator('[data-follow-tag]').click();
    await expect(page.locator('[data-follow-tag]')).toContainText(en ? 'Unfollow the tag' : 'Ne plus suivre le tag');
    const audit = await new AxeBuilder({ page }).include('app-tag-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    // Back returns to the feed the tag was reached from.
    await page.getByRole('button', { name: en ? 'Back' : 'Retour' }).click();
    await expect(page).toHaveURL(/\/network$/);
  });
}

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`reads the notifications grouped, the badge counting the unread (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const member = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: null, stack: [],
      city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
    let unread = 4;
    await page.route('**/api/network/notifications/unread', route => route.fulfill({ json: { unread } }));
    await page.route('**/api/network/notifications/read', route => {
      unread = 0;
      return route.fulfill({ status: 204 });
    });
    await page.route('**/api/network/notifications', route => route.fulfill({ json: [
      { type: 'LIKE', postId: 4, postExcerpt: 'Une question sur Kafka', actors: [member('leo_m', 'Léo'), member('ines_b', 'Inès')], count: 3,
        latestAt: '2026-11-14T10:00:00', read: false },
      { type: 'FOLLOW', postId: null, postExcerpt: null, actors: [member('paul_d', 'Paul')], count: 1, latestAt: '2026-11-13T10:00:00', read: true }] }));

    if (width > 600) {
      // The side menu shows on wide screens: its Network entry counts the unread notifications.
      await page.goto('/dashboard');
      await expect(page.getByRole('link', { name: en ? /Network/ : /Réseau/ }).first()).toContainText('4');
    }
    await page.goto('/network/notifications');
    await expect(page.locator('[data-notice="LIKE"]')).toContainText(en ? 'Léo and 2 others liked your post' : 'Léo et 2 autres ont aimé votre publication');
    await expect(page.locator('[data-notice="FOLLOW"]')).toContainText(en ? 'Paul started following you' : 'Paul vous suit désormais');
    await expect.poll(() => unread).toBe(0);
    const audit = await new AxeBuilder({ page }).include('app-network-notifications').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
  });
}

test('keeps a draft from the feed, then publishes it from the drafts', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
  let drafts: Record<string, unknown>[] = [];
  const asked: unknown[] = [];
  await page.route('**/api/network/drafts', route => {
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      asked.push(body);
      drafts = [{ id: 5, text: body.text, scheduledAt: body.scheduledAt, updatedAt: '2026-11-14T10:00:00' }];
      return route.fulfill({ status: 201, json: drafts[0] });
    }
    return route.fulfill({ json: drafts });
  });
  await page.route('**/api/network/drafts/5/publish', route => {
    asked.push('publish');
    return route.fulfill({ status: 201, json: {} });
  });
  const camille = { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null, headline: null, stack: [], city: null,
    remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
  await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: { card: camille, followers: 0, following: 0,
    sharedSkills: [], followed: false, followsYou: false, own: true, muted: false, blocked: false, privateCard: false, requested: false } }));
  await page.route('**/api/network/members/camille/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
  await page.goto('/network');
  await page.locator('[data-composer-text]').fill('À relire avant lundi');
  await page.locator('[data-keep-draft]').click();
  await expect(page.locator('[data-composer-text]')).toHaveValue('');
  // The drafts are a tab of the reader's page, one click away from the card beside the feed.
  await page.locator('[data-me-drafts]:visible').click();
  await expect(page).toHaveURL(/\/m\/camille\?tab=drafts$/);
  await expect(page.locator('[data-draft="5"]')).toContainText('À relire avant lundi');
  const audit = await new AxeBuilder({ page }).include('app-network-drafts').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await page.locator('[data-draft="5"] [data-draft-publish]').click();
  await expect(page.locator('[data-draft="5"]')).toHaveCount(0);
  expect(asked).toEqual([{ text: 'À relire avant lundi', scheduledAt: null }, 'publish']);
});
