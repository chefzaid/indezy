import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`passes a mission on with its facts (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    const posted: Record<string, unknown>[] = [];
    await page.route('**/api/network/posts', route => {
      const body = route.request().postDataJSON();
      posted.push(body);
      return route.fulfill({ status: 201, json: { id: 3, text: body.text, tags: [], createdAt: '2026-11-15T10:00:00', own: true, likes: 0,
        liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false,
        kind: body.kind, details: body.details, answerCommentId: null, author: { handle: 'camille', firstName: 'Camille', lastName: 'Durand',
          avatar: null, headline: null, stack: [], city: null, remote: false, availability: null, availabilityDate: null, languages: [],
          verified: false } } });
    });

    await page.goto('/network');
    if (width < 600) {
      await page.locator('[data-composer-open]').click();
    }
    await page.locator('[data-composer-text]').fill('Je ne peux pas la prendre');
    await page.locator('[data-post-kind]').click();
    await page.locator('[data-post-kind-option="MISSION"]').click();
    await expect(page.locator('[data-publish]')).toBeDisabled();
    await expect(page.locator('[data-keep-draft]')).toBeDisabled();
    await page.locator('[data-fact="role"]').fill('Lead Java');
    await page.locator('[data-fact="rateMin"]').fill('600');
    await page.locator('[data-fact="rateMax"]').fill('700');
    await page.locator('[data-fact="city"]').fill('Lyon');
    await page.locator('[data-fact="stack"]').fill('Java, Kafka');
    const audit = await new AxeBuilder({ page }).include('[data-composer]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.locator('[data-publish]').click();

    const card = page.locator('[data-post="3"]');
    await expect(card.locator('[data-post-kind-badge="MISSION"]')).toContainText(en ? 'Mission to pass on' : 'Mission à transmettre');
    await expect(card.locator('[data-fact-rate]')).toContainText('600–700 €');
    await expect(card.locator('[data-post-facts]')).toContainText('Java, Kafka');
    expect(posted).toEqual([{ text: 'Je ne peux pas la prendre', kind: 'MISSION', details: { role: 'Lead Java', rateMin: 600, rateMax: 700,
      city: 'Lyon', remoteDays: null, start: null, client: null, stack: ['Java', 'Kafka'] }, poll: null }]);
    await expect(page.locator('[data-post-facts]')).toHaveCount(1);
  });
}

test('asks a poll and votes in another member\'s', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  const author = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null,
    remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
  const poll = (votes: (number | null)[], myChoice: number | null) => ({ choices: ['IntelliJ', 'VS Code'].map((text, index) =>
    ({ text, votes: votes[index] })), endsAt: '2026-11-18T10:00:00Z', closed: false, myChoice,
    total: votes[0] === null ? null : votes.reduce((sum: number, count) => sum + (count ?? 0), 0) });
  const post = (votes: (number | null)[], myChoice: number | null) => ({ id: 5, text: 'Quel IDE ?', tags: [], createdAt: '2026-11-15T09:00:00',
    own: false, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0,
    repostsOff: false, kind: 'POLL', details: null, answerCommentId: null, poll: poll(votes, myChoice), author });
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [post([null, null], null)], page: 0, more: false } }));
  await page.route('**/api/network/posts/5/vote?**', route => route.fulfill({ json: post([1, 3], 1) }));
  const posted: Record<string, unknown>[] = [];
  await page.route('**/api/network/posts', route => {
    posted.push(route.request().postDataJSON());
    return route.fulfill({ status: 202, json: { held: true } });
  });

  await page.goto('/network');
  await page.locator('[data-post="5"] [data-poll-vote="1"]').click();
  await expect(page.locator('[data-post="5"] [data-poll-result="1"]')).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('[data-post="5"] [data-poll-result="1"]')).toContainText('75 %');
  await expect(page.locator('[data-post="5"] [data-post-poll]')).toContainText('4 votes');

  await page.locator('[data-composer-text]').fill('Votre langage préféré ?');
  await page.locator('[data-post-kind]').click();
  await page.locator('[data-post-kind-option="POLL"]').click();
  await page.locator('[data-poll-choice="0"]').fill('Java');
  await page.locator('[data-poll-choice="1"]').fill('Kotlin');
  await page.locator('[data-poll-add]').click();
  await page.locator('[data-poll-choice="2"]').fill('Scala');
  const audit = await new AxeBuilder({ page }).include('app-network-feed').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await page.locator('[data-publish]').click();
  await expect(page.getByText('Held for review')).toBeVisible();
  expect(posted).toEqual([{ text: 'Votre langage préféré ?', kind: 'POLL', details: null,
    poll: { choices: ['Java', 'Kotlin', 'Scala'], days: 3 } }]);
});

test('adds another member\'s mission to the pipeline, letting them know', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  const author = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: null, stack: [], city: null,
    remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [{ id: 6, text: 'Je ne peux pas la prendre', tags: [],
    createdAt: '2026-11-15T09:00:00', own: false, likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false,
    repostOf: null, reposts: 0, repostsOff: false, kind: 'MISSION', details: { role: 'Lead Java', rateMin: 600, rateMax: 700, city: 'Lyon',
      remoteDays: 2, start: null, client: null, stack: [] }, answerCommentId: null, poll: null, taken: { count: null, mine: false },
    author }], page: 0, more: false } }));
  const takes: string[] = [];
  await page.route('**/api/network/posts/6/pipeline?**', route => {
    takes.push(new URL(route.request().url()).searchParams.get('named')!);
    return route.fulfill({ status: 201, json: { id: 42, role: 'Lead Java', status: 'CONTACT' } });
  });

  await page.goto('/network');
  await page.locator('[data-post="6"] [data-mission-add]').click();
  await page.locator('[data-mission-add-named]').click();
  await expect(page.locator('[data-post="6"] [data-mission-taken]')).toContainText('In your pipeline');
  await expect(page.locator('[data-post="6"] [data-mission-open]')).toHaveAttribute('href', '/projects/42');
  expect(takes).toEqual(['true']);
  const audit = await new AxeBuilder({ page }).include('[data-post="6"]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});

test('switches the feed to For you and back', async ({ page }) => {
  await mockWorkspaceSession(page, 'fr');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  const author = { handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard', avatar: null, headline: null, stack: [], city: null,
    remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
  const post = (id: number, text: string) => ({ id, text, tags: [], createdAt: '2026-11-15T09:00:00', own: false, likes: 0, liked: false,
    comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false, kind: 'POST',
    details: null, answerCommentId: null, poll: null, taken: null, author });
  await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [post(1, 'Suivi')], page: 0, more: false } }));
  await page.route('**/api/network/feed/for-you?**', route => route.fulfill({ json: { posts: [post(2, 'Pour vous')], page: 0, more: false } }));

  await page.goto('/network');
  await expect(page.locator('[data-post="1"]')).toBeVisible();
  await page.locator('[data-feed-tab-for-you]').click();
  await expect(page.locator('[data-post="2"]')).toContainText('Pour vous');
  await expect(page.locator('[data-post="1"]')).toHaveCount(0);
  await page.locator('[data-feed-tab-following]').click();
  await expect(page.locator('[data-post="1"]')).toBeVisible();
});
