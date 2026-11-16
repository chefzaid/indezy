import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const leo = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: 'Data engineer', stack: [], city: null,
  remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
const post = { id: 7, text: 'Retour sur #Kafka en prod.', tags: ['kafka'], createdAt: new Date().toISOString(), own: false, likes: 2,
  liked: false, comments: 1, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false,
  kind: 'POST', details: null, answerCommentId: null, poll: null, taken: null, useful: null, reactions: [], myReaction: null, author: leo };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`opens a post from its notification, its comments open (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/notifications/unread', route => route.fulfill({ json: { unread: 1 } }));
    await page.route('**/api/network/notifications/read', route => route.fulfill({ status: 204 }));
    await page.route('**/api/network/notifications', route => route.fulfill({ json: [{ type: 'COMMENT', postId: 7,
      postExcerpt: 'Retour sur #Kafka', actors: [leo], count: 1, latestAt: new Date().toISOString(), read: false }] }));
    await page.route('**/api/network/posts/7', route => route.fulfill({ json: post }));
    await page.route('**/api/network/posts/7/comments', route => route.fulfill({ json: [{ id: 3, text: 'Merci pour le retour', author: leo,
      createdAt: new Date().toISOString(), own: false, hidden: false, pinned: false, likes: 0, liked: false, replies: [] }] }));
    await page.route('**/api/network/posts/8', route => route.fulfill({ status: 404, json: { message: 'Not found' } }));

    await page.goto('/network/notifications');
    await page.locator('[data-notice-link]').first().click();
    await expect(page).toHaveURL(/\/network\/posts\/7$/);
    await expect(page.locator('[data-post="7"]')).toContainText('Retour sur');
    await expect(page.getByText('Merci pour le retour')).toBeVisible();
    const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await page.goto('/network/posts/8');
    await expect(page.locator('[data-post-missing]')).toContainText(en ? 'post' : 'publication');
  });
}
