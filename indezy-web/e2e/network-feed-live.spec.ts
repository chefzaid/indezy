import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const leo = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false,
  availability: null, availabilityDate: null, languages: [], verified: false };
const post = (id: number) => ({ id, text: `Publication ${id}`, tags: [], createdAt: `2026-11-16T10:${String(id).padStart(2, '0')}:00`,
  own: false, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0,
  repostsOff: false, kind: 'POST', details: null, answerCommentId: null, poll: null, taken: null, useful: null, reactions: [], myReaction: null, preview: null, author: leo });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`loads more posts on scrolling, offers the newer ones and shows the trending tags (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.clock.install({ time: new Date('2026-11-16T11:00:00') });
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/tags/trending', route => route.fulfill({ json: [{ tag: 'kafka', posts: 12 }, { tag: 'java', posts: 1 }] }));
    let fresh = false;
    const pages: string[] = [];
    await page.route('**/api/network/feed?**', route => {
      const asked = new URL(route.request().url()).searchParams.get('page')!;
      pages.push(asked);
      const first = Array.from({ length: 20 }, (_, index) => post(59 - index));
      const posts = asked === '1' ? [post(30), post(29)] : fresh ? [post(60), ...first] : first;
      return route.fulfill({ json: { posts, page: Number(asked), more: asked === '0' } });
    });
    await page.route('**/api/network/feed/newer?**', route => route.fulfill({ json: { count: fresh ? 1 : 0 } }));

    await page.goto('/network');
    await expect(page.locator('[data-post="59"]')).toBeVisible();
    await expect(page.locator('[data-trending-tag="kafka"]')).toContainText(en ? '12 posts' : '12 publications');
    await expect(page.locator('[data-trending-tag="java"]')).toContainText(en ? '1 post' : '1 publication');

    // Near the end of the feed, the next page comes on its own.
    await page.locator('[data-post="40"]').scrollIntoViewIfNeeded();
    await expect(page.locator('[data-post="29"]')).toBeAttached();
    expect(pages).toEqual(['0', '1']);

    // A minute later a newer post is offered; the pill reads the feed again from the top.
    fresh = true;
    await page.clock.fastForward(60_000);
    const pill = page.locator('[data-feed-newer]');
    await expect(pill).toContainText(en ? '1 new post' : '1 nouvelle publication');
    const audit = await new AxeBuilder({ page }).include('app-network-feed').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await pill.click();
    await expect(page.locator('[data-post="60"]')).toBeVisible();
    await expect(pill).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
