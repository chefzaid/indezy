import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const author = { handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard', avatar: null, headline: null, stack: [], city: null,
  remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
const idea = (id: number, title: string, changes: Record<string, unknown> = {}) => ({ id, title, description: 'Le besoin, un exemple.',
  status: 'OPEN', statusNote: null, votes: 3, voted: false, own: false, author, createdAt: '2026-11-18T09:00:00', statusChangedAt: null,
  mergedInto: null, ...changes });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`votes for an idea, sees the similar ones while writing, and proposes one (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const board = [idea(4, 'Export CSV des factures', { votes: 12 }), idea(7, 'Thème sombre', { status: 'PLANNED', statusNote: 'Au printemps' }),
      idea(9, 'Signature électronique', { status: 'DONE', votes: 30 })];
    const asked: string[] = [];
    await page.route('**/api/network/ideas?**', route => {
      const url = new URL(route.request().url());
      asked.push(url.searchParams.get('q') ?? '');
      const typed = (url.searchParams.get('q') ?? '').toLowerCase();
      return route.fulfill({ json: { ideas: board.filter(item => item.title.toLowerCase().includes(typed)), page: 0, more: false, administrator: false } });
    });
    const votes: string[] = [];
    await page.route('**/api/network/ideas/4/vote', route => {
      votes.push(route.request().method());
      return route.fulfill({ json: { ...board[0], votes: 13, voted: true } });
    });
    const proposed: Record<string, unknown>[] = [];
    await page.route('**/api/network/ideas', route => {
      proposed.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: idea(15, 'Export des devis', { votes: 1, voted: true, own: true }) });
    });

    await page.goto('/network/ideas');
    await expect(page.locator('[data-idea]')).toHaveCount(3);
    await expect(page.locator('[data-idea="7"] [data-idea-note]')).toContainText('Au printemps');
    await expect(page.locator('[data-idea="9"] [data-idea-vote]')).toBeDisabled();
    await page.locator('[data-idea="4"] [data-idea-vote]').click();
    await expect(page.locator('[data-idea="4"] [data-idea-votes]')).toHaveText('13');
    await expect(page.locator('[data-idea="4"] [data-idea-vote]')).toHaveAttribute('aria-pressed', 'true');
    expect(votes).toEqual(['PUT']);

    await page.locator('[data-idea-new]').click();
    await page.locator('[data-idea-title]').fill('Export');
    await expect(page.locator('[data-idea-similar-item="4"]')).toBeVisible();
    await expect(page.locator('[data-idea-similar]')).toContainText(en ? 'Similar ideas already exist' : 'Des idées proches existent déjà');
    const audit = await new AxeBuilder({ page }).include('main').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('[data-idea-title]').fill('Export des devis');
    await page.locator('[data-idea-create]').click();
    await expect(page.locator('[data-idea="15"]')).toBeVisible();
    expect(proposed).toEqual([{ title: 'Export des devis', description: null }]);
    expect(asked).toContain('Export');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
}

test('lets an administrator answer an idea, its note shown', async ({ page }) => {
  await mockWorkspaceSession(page, 'fr');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: true, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/ideas?**', route => route.fulfill({ json: { ideas: [idea(4, 'Export CSV')], page: 0, more: false, administrator: true } }));
  const answers: Record<string, unknown>[] = [];
  await page.route('**/api/network/ideas/4/status', route => {
    answers.push(route.request().postDataJSON());
    return route.fulfill({ json: idea(4, 'Export CSV', { status: 'IN_PROGRESS', statusNote: 'En test' }) });
  });

  await page.goto('/network/ideas');
  await page.locator('[data-idea="4"] [data-idea-actions]').click();
  await page.locator('[data-idea-answer]').click();
  await page.locator('[data-idea-answer-status]').click();
  await page.getByRole('option', { name: 'En cours' }).click();
  await page.locator('[data-idea-answer-note]').fill('En test');
  await page.locator('[data-idea-answer-save]').click();
  await expect(page.locator('[data-idea="4"] [data-idea-status]')).toContainText('En cours');
  await expect(page.locator('[data-idea="4"] [data-idea-note]')).toContainText('En test');
  expect(answers).toEqual([{ status: 'IN_PROGRESS', note: 'En test' }]);
});
