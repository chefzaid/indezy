import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`anonymizes the workspace's names before a post goes out (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    await page.route('**/api/network/confidentiality', route => {
      const text: string = route.request().postDataJSON().text;
      return route.fulfill({ json: text.includes('Société Générale') ?
        [{ found: 'Société Générale', kind: 'CLIENT', city: 'Paris' }] : [] });
    });
    const posted: string[] = [];
    await page.route('**/api/network/posts', route => {
      posted.push(route.request().postDataJSON().text);
      return route.fulfill({ status: 201, json: { id: 2, text: posted.at(-1), tags: [], createdAt: '2026-11-14T10:00:00', own: true,
        likes: 0, liked: false, comments: 0, commentsClosed: false, edited: false, bookmarked: false, repostOf: null, reposts: 0,
        repostsOff: false, author: { handle: 'camille', firstName: 'Camille', lastName: 'Durand', avatar: null, headline: null, stack: [],
          city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false } } });
    });

    await page.goto('/network');
    if (width < 600) {
      await page.locator('[data-composer-open]').click();
    }
    await page.locator('[data-composer-text]').fill('Fin de mission chez Société Générale');
    await page.locator('[data-publish]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.locator('[data-confidential="Société Générale"]')).toContainText(en ? 'a client in Paris' : 'un client à Paris');
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-confidential-anonymize]').click();
    await expect(page.locator('[data-composer-text]')).toHaveValue(en ? 'Fin de mission chez a client in Paris' :
      'Fin de mission chez un client à Paris');
    expect(posted).toEqual([]);
    await page.locator('[data-publish]').click();
    await expect(page.locator('[data-post="2"]')).toBeVisible();
    expect(posted).toEqual([en ? 'Fin de mission chez a client in Paris' : 'Fin de mission chez un client à Paris']);
  });
}
