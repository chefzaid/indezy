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
    await page.route('**/api/network/mentions?**', route => route.fulfill({ json: [card('leo_m', 'Léo')] }));
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
      expect(feed.width).toBeLessThanOrEqual(681);
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

    // Enter searches the members by the skill typed.
    await page.route('**/api/network/members?**', route => route.fulfill({ json: { members: [card('leo_m', 'Léo')], total: 1, page: 0,
      size: 20, followed: [], requested: [], privateCards: [] } }));
    await page.locator('[data-network-search-field]').fill('Kafka');
    await page.locator('[data-network-search-field]').press('Enter');
    await expect(page).toHaveURL(/\/network\/members\?skill=Kafka$/);
    await expect(page.locator('[data-directory-skill], input[formcontrolname="skill"]').first()).toHaveValue('Kafka');
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
