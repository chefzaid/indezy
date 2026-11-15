import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const author = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Durand', avatar: null, headline: null, stack: [],
  city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });
const post = { id: 1, text: 'Achetez mes formations', tags: [], createdAt: '2026-11-13T09:00:00', own: false, likes: 0, liked: false,
  comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0, repostsOff: false,
  author: author('leo_m', 'Léo') };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`reports a post to the moderators with a reason (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [post], page: 0, more: false } }));
    const reports: unknown[] = [];
    await page.route('**/api/network/posts/1/report', route => {
      reports.push(route.request().postDataJSON());
      return route.fulfill({ status: 204 });
    });

    await page.goto('/network');
    await page.locator('[data-post="1"] [data-post-actions]').click();
    await page.locator('[data-post-report]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(en ? 'Report this post' : 'Signaler cette publication');
    await dialog.locator('[data-report-send]').click();
    await expect(dialog).toContainText(en ? 'Choose a reason' : 'Choisissez un motif');
    await dialog.locator('[data-report-reason]').click();
    await page.locator('[data-report-option="SPAM"]').click();
    await dialog.locator('[data-report-details]').fill('Publicité');
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await dialog.locator('[data-report-send]').click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    expect(reports).toEqual([{ reason: 'SPAM', details: 'Publicité' }]);
  });
}

for (const width of [1280, 390]) {
  test(`an administrator removes a reported post from the moderation queue (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: true, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/admin/overview', route => route.fulfill({ json: { accounts: 40, disabledAccounts: 2, signupsLast30Days: 5,
      featureWorkspaces: {} } }));
    const open = { id: 7, targetType: 'POST', targetId: 1, target: { accountId: 3, handle: 'leo_m', name: 'Léo Durand' },
      excerpt: 'Achetez mes formations', reason: 'SPAM', details: 'Publicité', reporter: { accountId: 4, handle: 'ines_b', name: 'Inès B' },
      createdAt: '2026-11-14T10:00:00', openOnTarget: 2, outcome: null, resolvedBy: null, resolvedAt: null, note: null };
    let queue: Record<string, unknown> = { open: [open], resolved: [], openCount: 1 };
    const decisions: unknown[] = [];
    await page.route('**/api/admin/network/reports', route => route.fulfill({ json: queue }));
    await page.route('**/api/admin/network/reports/7', route => {
      decisions.push(route.request().postDataJSON());
      queue = { open: [], resolved: [{ ...open, outcome: 'REMOVED', resolvedBy: { accountId: 9, handle: null, name: 'Chief Admin' },
        resolvedAt: '2026-11-14T11:00:00Z', note: 'Spam commercial' }], openCount: 0 };
      return route.fulfill({ json: queue });
    });

    await page.goto('/admin');
    await page.getByRole('tab', { name: 'Moderation' }).click();
    const report = page.locator('[data-report="7"]');
    await expect(report).toContainText('Achetez mes formations');
    await expect(report.locator('[data-report-count]')).toContainText('2 reports');
    await report.locator('[data-report-note]').fill('Spam commercial');
    const audit = await new AxeBuilder({ page }).include('app-admin-moderation').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await report.locator('[data-decide="REMOVED"]').click();
    await expect(page.locator('[data-moderation-empty]')).toBeVisible();
    await expect(page.locator('[data-resolved="7"]')).toContainText('Chief Admin');
    expect(decisions).toEqual([{ outcome: 'REMOVED', note: 'Spam commercial', days: null }]);
  });
}
