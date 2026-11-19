import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession, themeByWidth } from './helpers/workspace';

const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };
const card = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Durand', avatar: null, headline: 'Architecte Java',
  stack: ['Java'], city: 'Lyon', remote: false, availability: null, availabilityDate: null, languages: [], verified: false });

for (const [language, width] of [['en', 1920], ['fr', 1366], ['en', 390]] as const) {
  test(`keeps the reader's page beside the feed and searches the network (${language}, ${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
    await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: { card: card('camille', 'Camille'), followers: 12,
      following: 4, sharedSkills: [], followed: false, followsYou: false, own: true, muted: false, blocked: false, privateCard: false,
      requested: false } }));
    await page.route('**/api/network/members/camille/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    await page.route('**/api/network/members/camille/followers', route => route.fulfill({ json: [card('leo_m', 'Léo')] }));
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    await page.route('**/api/network/search/members?**', route => route.fulfill({ json: { members: [card('leo_m', 'Léo')], total: 1, page: 0,
      size: 20, followed: [], requested: [], privateCards: [] } }));
    await page.route('**/api/network/search/posts?**', route => route.fulfill({ json: { posts: [{ id: 7, text: 'Retour sur Kafka en prod',
      tags: [], createdAt: '2026-11-15T09:00:00', own: false, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false,
      bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST', details: null, answerCommentId: null, poll: null,
      taken: null, author: card('leo_m', 'Léo') }], page: 0, more: false } }));
    await page.route('**/api/network/tags/suggest?**', route => route.fulfill({ json: ['java'] }));

    await page.goto('/network');
    // The tabs, after the search, in this order.
    const tabs = page.locator('[data-network-nav] a.nav-link');
    await expect(tabs).toHaveCount(4);
    expect(await tabs.evaluateAll(links => links.map(link => link.getAttribute('href'))))
      .toEqual(['/network', '/network/messages', '/network/members', '/network/notifications']);
    // Every tab in sight, a phone's too (icons, the open one named).
    for (const box of await tabs.evaluateAll(links => links.map(link => link.getBoundingClientRect().right))) {
      expect(box).toBeLessThanOrEqual(width);
    }
    const me = page.locator('[data-me-card]:visible');
    await expect(me).toHaveCount(1);
    await expect(me).toContainText('Camille Durand');
    await expect(me.locator('[data-me-saved]')).toBeVisible();
    await expect(me.locator('[data-me-drafts]')).toBeVisible();
    const feed = (await page.locator('[data-feed-tabs]').boundingBox())!;
    if (width >= 1100) {
      await expect(me.locator('[data-me-followers]')).toContainText('12');
      // The posts keep a reading width however wide the screen.
      expect(feed.width).toBeLessThanOrEqual(721);
    } else {
      // One column: the card is a strip above the posts.
      expect((await me.boundingBox())!.height).toBeLessThan(160);
    }
    if (width >= 1440) {
      // The card is the left column, the posts between it and the side cards.
      expect((await me.boundingBox())!.x).toBeLessThan(feed.x);
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    const audit = await new AxeBuilder({ page }).include('app-network-feed').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);

    // The search suggests members and tags; a member picked opens their page.
    await page.locator('[data-network-search-field]').fill('le');
    await expect(page.locator('[data-search-member="leo_m"]')).toBeVisible();
    await expect(page.locator('[data-search-tag="java"]')).toBeVisible();
    await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: { card: card('leo_m', 'Léo'), followers: 1,
      following: 0, sharedSkills: [], followed: false, followsYou: false, own: false, muted: false, blocked: false, privateCard: false,
      requested: false } }));
    await page.route('**/api/network/members/leo_m/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    await page.locator('[data-search-member="leo_m"]').click();
    await expect(page).toHaveURL(/\/m\/leo_m$/);
    await expect(page.locator('[data-member-card="leo_m"]')).toBeVisible();

    // Enter opens the results: the members, the tags and the posts found, each tab kept in the address.
    await page.locator('[data-network-search-field]').fill('Kafka');
    await page.locator('[data-network-search-field]').press('Enter');
    await expect(page).toHaveURL(/\/network\/search\?q=Kafka$/);
    await expect(page.locator('[data-search-result-member="leo_m"]')).toBeVisible();
    await expect(page.locator('[data-search-result-tag="java"]')).toBeVisible();
    await expect(page.locator('[data-search-posts]')).toContainText('Retour sur Kafka en prod');
    const results = await new AxeBuilder({ page }).include('app-network-search-results').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(results.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.locator('[data-search-tab="posts"]').click();
    await expect(page).toHaveURL(/tab=posts/);
    await expect(page.locator('[data-search-members]')).toHaveCount(0);
    await expect(page.locator('[data-search-posts] app-post-card')).toHaveCount(1);
  });
}

test('opens the followers of the reader from the card beside the feed', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 });
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
  await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: { card: card('camille', 'Camille'), followers: 1,
    following: 0, sharedSkills: [], followed: false, followsYou: false, own: true, muted: false, blocked: false, privateCard: false,
    requested: false } }));
  await page.route('**/api/network/members/camille/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
  await page.route('**/api/network/members/camille/followers', route => route.fulfill({ json: [card('leo_m', 'Léo')] }));
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
  await page.goto('/network');
  await page.locator('[data-me-followers]:visible').click();
  await expect(page).toHaveURL(/\/m\/camille\?tab=followers$/);
  await expect(page.locator('[data-member-list="followers"]')).toContainText('Léo');
});

for (const [language, width] of [['fr', 1600], ['en', 390]] as const) {
  test(`filters the notifications by kind and opens their settings (${language}, ${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
    await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: { card: card('camille', 'Camille'), followers: 1,
      following: 1, sharedSkills: [], followed: false, followsYou: false, own: true, muted: false, blocked: false, privateCard: false,
      requested: false } }));
    const leo = card('leo_m', 'Léo');
    const notice = (type: string, read: boolean) => ({ type, count: 1, read, postId: type === 'FOLLOW' ? null : 4,
      postExcerpt: type === 'FOLLOW' ? null : 'Une question sur Kafka', latestAt: new Date().toISOString(), actors: [leo] });
    await page.route('**/api/network/notifications', route => route.fulfill({ json: [notice('MENTION', false), notice('LIKE', true),
      notice('FOLLOW', true)] }));
    await page.route('**/api/network/notifications/read', route => route.fulfill({ status: 204 }));
    await page.goto('/network/notifications');
    await expect(page.locator('[data-notice]')).toHaveCount(3);
    const filter = (key: string) => page.locator(width >= 1440 ? `[data-notice-filter="${key}"]` : `[data-notice-chip="${key}"]`);
    await filter('follows').click();
    await expect(page.locator('[data-notice]')).toHaveCount(1);
    await expect(page.locator('[data-notice="FOLLOW"]')).toBeVisible();
    await filter('unread').click();
    await expect(page.locator('[data-notice="MENTION"]')).toBeVisible();
    await expect(page.locator('[data-notice]')).toHaveCount(1);
    const audit = await new AxeBuilder({ page }).include('app-network-notifications').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    if (width >= 1440) {
      await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Camille', lastName: 'Durand',
        email: 'workspace@example.test' } }));
      await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
      await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
      await page.route('**/api/network/restrictions', route => route.fulfill({ json: { muted: [], blocked: [] } }));
      await page.route('**/api/network/messages/settings', route => route.fulfill({ json: { readReceipts: true, typing: true } }));
      await page.route('**/api/network/notifications/settings', route => route.fulfill({ json: { off: [], emailed: [] } }));
      await page.locator('[data-notice-settings]').click();
      await expect(page).toHaveURL(/\/profile\?tab=network$/);
      // The profile opens on its Network tab, where the notifications are set.
      await expect(page.locator('[data-profile-network]')).toBeVisible();
    }
  });
}

for (const [language, width] of [['fr', 1600], ['en', 390]] as const) {
  test(`reads a member's recommendations, and asks for one from one's own page (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, width);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false,
      features: { SOCIAL_NETWORK: true, PUBLIC_PROFILES: true } } }));
    await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
    const memberPage = (handle: string, own: boolean) => ({ card: card(handle, own ? 'Camille' : 'Léo'), followers: 3, following: 1,
      sharedSkills: [], followed: true, followsYou: true, own, muted: false, blocked: false, privateCard: false, requested: false });
    await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: memberPage('camille', true) }));
    await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: memberPage('leo_m', false) }));
    await page.route('**/api/network/members/*/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    await page.route('**/api/network/members/leo_m/recommendations', route => route.fulfill({ json: [
      { authorName: 'Léa Martin', authorTitle: 'CTO, Acme', text: 'Un consultant rigoureux, qui a mené la refonte en temps et en heure.' }] }));
    await page.route('**/api/network/members/camille/recommendations', route => route.fulfill({ json: [] }));

    await page.goto('/m/leo_m');
    await page.locator('[data-tab-recommendations]').click();
    await expect(page.locator('[data-recommendation]')).toContainText('CTO, Acme');
    await expect(page.locator('[data-ask-recommendation]')).toHaveCount(0);
    const audit = await new AxeBuilder({ page }).include('app-member-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await page.goto('/m/camille?tab=recommendations');
    await expect(page.locator('[data-recommendations]')).toContainText(en ? 'No recommendation yet' : 'Aucune recommandation');
    await expect(page.locator('[data-ask-recommendation]')).toHaveAttribute('href', '/profile?tab=public');
  });
}

test('tells a member who viewed their page, and browses privately', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 });
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
  const memberPage = (handle: string, own: boolean) => ({ card: card(handle, own ? 'Camille' : 'Léo'), followers: 3, following: 1,
    sharedSkills: [], followed: false, followsYou: false, own, muted: false, blocked: false, privateCard: false, requested: false });
  await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: memberPage('camille', true) }));
  await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: memberPage('leo_m', false) }));
  await page.route('**/api/network/members/*/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
  await page.route('**/api/network/members/*/recommendations', route => route.fulfill({ json: [] }));
  const viewed: string[] = [];
  await page.route('**/api/network/members/*/views', route => {
    viewed.push(route.request().url().split('/').at(-2)!);
    return route.fulfill({ status: 204 });
  });
  let views = { viewers: 2, viewsThisWeek: 3, named: [{ card: card('leo_m', 'Léo'), viewedAt: new Date().toISOString() }], anonymous: 1,
    privateViewing: false };
  await page.route('**/api/network/page-views', route => route.fulfill({ json: views }));
  await page.route('**/api/network/page-views/private?**', route => {
    views = { ...views, named: [], anonymous: 2, privateViewing: true };
    return route.fulfill({ json: views });
  });

  await page.goto('/m/leo_m');
  await expect.poll(() => viewed).toEqual(['leo_m']);
  await page.goto('/network/members');
  await page.goto('/m/camille');
  await expect(page.locator('[data-me-viewers]:visible')).toContainText('2 views of your page');
  await page.locator('[data-me-viewers]:visible').click();
  await expect(page).toHaveURL(/tab=viewers/);
  await expect(page.locator('[data-viewer="leo_m"]')).toBeVisible();
  await expect(page.locator('[data-viewers-anonymous]')).toContainText('1 member browsing privately');
  const audit = await new AxeBuilder({ page }).include('app-network-viewers').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await page.locator('[data-private-viewing] button').click();
  await expect(page.locator('[data-viewer]')).toHaveCount(0);
  await expect(page.locator('[data-viewers-anonymous]')).toContainText('unnamed while you browse privately');
  // One's own page is never counted.
  expect(viewed).toEqual(['leo_m']);
});

test('shows an author their post\'s analytics and their activity, and counts others\' posts shown', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 });
  await mockWorkspaceSession(page, 'fr');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
  await page.route('**/api/network/members/camille/page', route => route.fulfill({ json: { card: card('camille', 'Camille'), followers: 3,
    following: 1, sharedSkills: [], followed: false, followsYou: false, own: true, muted: false, blocked: false, privateCard: false,
    requested: false } }));
  const post = (id: number, own: boolean) => ({ id, text: own ? 'Mon retour sur Kafka' : 'Le retour de Léo', tags: [],
    createdAt: '2026-11-15T09:00:00', own, likes: 4, liked: false, comments: 2, commentsClosed: false, edited: false, bookmarked: false,
    repostOf: null, reposts: 1, repostsOff: false, kind: 'POST', details: null, answerCommentId: null, poll: null, taken: null,
    author: own ? card('camille', 'Camille') : card('leo_m', 'Léo') });
  await page.route('**/api/network/members/camille/posts?**', route => route.fulfill({ json: { posts: [post(3, true)], page: 0, more: false } }));
  await page.route('**/api/network/members/camille/recommendations', route => route.fulfill({ json: [] }));
  await page.route('**/api/network/activity', route => route.fulfill({ json: { postsThisWeek: 2, reachedThisWeek: 48, followersGained: 5,
    missionsPassedOn: 1, missionsTaken: 3 } }));
  await page.route('**/api/network/posts/3/stats', route => route.fulfill({ json: { impressions: 130, reached: 48, saves: 6 } }));
  const shown: number[][] = [];
  await page.route('**/api/network/posts/impressions', route => {
    shown.push(route.request().postDataJSON().ids);
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [post(8, false), post(3, true)], page: 0, more: false } }));

  await page.goto('/m/camille');
  await expect(page.locator('[data-activity-reached]')).toHaveText('48');
  await page.locator('[data-post="3"] [data-post-insights]').click();
  await expect(page.locator('[data-post-figures]')).toContainText('130');
  await expect(page.locator('[data-post-figures]')).toContainText('Membres atteints');
  const audit = await new AxeBuilder({ page }).include('app-member-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);

  // Another member's post shown in the feed is counted for its author; one's own is not.
  await page.goto('/network');
  await expect(page.locator('[data-post="8"]')).toBeVisible();
  await expect.poll(() => shown.flat(), { timeout: 8000 }).toEqual([8]);
});

test('shows the post a member pinned first, their About section and the missions they won', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 });
  await mockWorkspaceSession(page, 'fr');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
  await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: { card: card('leo_m', 'Léo'), followers: 3, following: 1,
    sharedSkills: [], followed: true, followsYou: false, own: false, muted: false, blocked: false, privateCard: false, requested: false,
    pinnedPostId: 5 } }));
  const post = (id: number, text: string) => ({ id, text, tags: [], createdAt: '2026-11-15T09:00:00', own: false, likes: 0, liked: false,
    comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST',
    details: null, answerCommentId: null, poll: null, taken: null, author: card('leo_m', 'Léo') });
  await page.route('**/api/network/posts/5', route => route.fulfill({ json: post(5, 'Qui je suis, ce que je cherche') }));
  await page.route('**/api/network/members/leo_m/posts?**', route => route.fulfill({ json: { posts: [post(6, 'Un retour récent'),
    post(5, 'Qui je suis, ce que je cherche')], page: 0, more: false } }));
  await page.route('**/api/network/members/leo_m/about', route => route.fulfill({ json: { about: 'Architecte Java depuis 12 ans.',
    missions: [{ role: 'Lead Kafka', stack: ['Java', 'Kafka'], start: '2026-02-02', end: null }] } }));
  await page.route('**/api/network/members/leo_m/recommendations', route => route.fulfill({ json: [] }));
  await page.route('**/api/network/members/leo_m/mutual', route => route.fulfill({ json: { count: 0, members: [] } }));
  await page.route('**/api/network/members/leo_m/views', route => route.fulfill({ status: 204 }));

  await page.goto('/m/leo_m');
  await expect(page.locator('[data-about-text]')).toHaveText('Architecte Java depuis 12 ans.');
  await expect(page.locator('[data-past-mission]')).toContainText('Lead Kafka');
  await expect(page.locator('[data-past-mission]')).toContainText('aujourd’hui');
  await expect(page.locator('[data-post]').first()).toHaveAttribute('data-post', '5');
  await expect(page.locator('[data-post="5"] [data-post-pinned]')).toContainText('Épinglé');
  await expect(page.locator('[data-post]')).toHaveCount(2);
  const audit = await new AxeBuilder({ page }).include('app-member-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});

test('sends a post in a message, read there as the post\'s card', async ({ page }) => {
  await page.setViewportSize({ width: 1600, height: 900 });
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille', fields } }));
  await page.route('**/api/network/members/camille/followers', route => route.fulfill({ json: [card('ines_b', 'Inès')] }));
  await page.route('**/api/network/members/camille/following', route => route.fulfill({ json: [card('leo_m', 'Léo')] }));
  const leo = card('leo_m', 'Léo');
  const post = { id: 8, text: 'Retour sur une migration Kafka', tags: [], createdAt: '2026-11-15T09:00:00', own: false, likes: 0, liked: false,
    comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST',
    details: null, answerCommentId: null, poll: null, taken: null, author: leo };
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [post], page: 0, more: false } }));
  await page.route('**/api/network/posts/8', route => route.fulfill({ json: post }));
  const conversation = { id: 4, with: leo, group: null, last: null, unread: 0, muted: false, archived: false, closed: null,
    lastMessageAt: null };
  await page.route('**/api/network/conversations?**', route => route.fulfill({ json: [conversation] }));
  const sent: { conversation: string; text: string }[] = [];
  let thread: Record<string, unknown>[] = [];
  await page.route('**/api/network/conversations/*/messages', route => {
    const text = route.request().postDataJSON().text as string;
    sent.push({ conversation: route.request().url().split('/').at(-2)!, text });
    const message = { id: 30, mine: true, from: null, text, sentAt: new Date().toISOString(), edited: false, deleted: false, editable: true,
      replyTo: null, reactions: [], myReaction: null, card: null };
    thread = [message];
    return route.fulfill({ status: 201, json: message });
  });
  await page.route('**/api/network/conversations', route => route.fulfill({ json: { ...conversation, id: 9, with: card('ines_b', 'Inès') } }));

  await page.goto('/network');
  await page.locator('[data-post="8"] [data-post-send]').click();
  await page.locator('[data-send-target="c4"] input').check();
  await page.locator('[data-send-target="mines_b"] input').check();
  await page.locator('[data-send-note]').fill('À lire');
  const audit = await new AxeBuilder({ page }).include('app-send-post-dialog').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await page.locator('[data-send-submit]').click();
  await expect(page.getByText('Sent in 2 conversations')).toBeVisible();
  expect(sent.map(message => message.conversation).sort()).toEqual(['4', '9']);
  expect(sent[0].text).toMatch(/^À lire\nhttp:\/\/[^/]+\/network\/posts\/8$/);

  await page.route('**/api/network/conversations/4', route => route.fulfill({ json: { conversation, more: false, messages: thread } }));
  await page.route('**/api/network/conversations/4/read', route => route.fulfill({ json: conversation }));
  await page.goto('/network/messages/4');
  await expect(page.locator('[data-shared-post]')).toContainText('Retour sur une migration Kafka');
  await expect(page.locator('[data-shared-post]')).toHaveAttribute('href', '/network/posts/8');
});
