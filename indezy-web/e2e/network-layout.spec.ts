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
