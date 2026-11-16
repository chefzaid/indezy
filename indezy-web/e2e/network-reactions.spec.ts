import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const post = (myReaction: string | null, reactions: { reaction: string; count: number }[]) => ({ id: 3, text: 'Mission signée !', tags: [],
  createdAt: '2026-11-16T09:00:00', own: false, likes: reactions.reduce((sum, item) => sum + item.count, 0), liked: myReaction !== null,
  comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST',
  details: null, answerCommentId: null, poll: null, taken: null, useful: { count: 0, mine: false }, myReaction, reactions,
  author: { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null, remote: false,
    availability: null, availabilityDate: null, languages: [], verified: false } });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`reacts to a post with another emoji, and saves it at once (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [post(null, [{ reaction: 'LIKE', count: 1 }])],
      page: 0, more: false } }));
    const asked: string[] = [];
    await page.route('**/api/network/posts/3/like**', route => {
      asked.push(`${route.request().method()} ${new URL(route.request().url()).searchParams.get('reaction')}`);
      return route.fulfill({ json: post('BRAVO', [{ reaction: 'BRAVO', count: 1 }, { reaction: 'LIKE', count: 1 }]) });
    });
    let saved: () => void = () => undefined;
    await page.route('**/api/network/posts/3/bookmark', route => new Promise<void>(resolve => saved = () => {
      void route.fulfill({ json: { ...post('BRAVO', []), bookmarked: true } });
      resolve();
    }));

    await page.goto('/network');
    const like = page.locator('[data-post="3"] [data-like]');
    await like.focus();
    await like.press('ArrowUp');
    await expect(page.locator('[data-react]')).toHaveCount(5);
    await expect(page.locator('[data-react="LIKE"]')).toBeFocused();
    const audit = await new AxeBuilder({ page }).include('.reaction-menu').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('[data-react="BRAVO"]').click();
    await expect(like).toHaveAttribute('data-reaction', 'BRAVO');
    await expect(like).toContainText('👏');
    await expect(page.locator('[data-post="3"] [data-likes]')).toContainText(en ? 'Reactions: 2' : 'Réactions : 2');
    expect(asked).toEqual(['PUT BRAVO']);

    // Saving shows at once, before the server answers.
    await page.locator('[data-post="3"] [data-bookmark]').click();
    await expect(page.locator('[data-post="3"] [data-bookmark]')).toHaveAttribute('aria-pressed', 'true');
    saved();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
