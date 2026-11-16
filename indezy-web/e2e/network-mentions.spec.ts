import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const member = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Durand', avatar: null, headline: null, stack: [],
  city: null, remote: false, availability: null, availabilityDate: null, languages: [], verified: false });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`completes a mention and a tag in the composer (${language}, ${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/feed?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    const asked: string[] = [];
    await page.route('**/api/network/mentions?**', route => {
      asked.push(new URL(route.request().url()).searchParams.get('q')!);
      return route.fulfill({ json: [member('camille', 'Camille'), member('cam_d', 'Camélia')] });
    });
    await page.route('**/api/network/tags/suggest?**', route => route.fulfill({ json: ['kafka', 'kafka-streams'] }));

    await page.goto('/network');
    if (width < 600) {
      await page.locator('[data-composer-open]').click();
    }
    const text = page.locator('[data-composer-text]');
    if (width < 600) {
      await expect(page.locator('[data-composer-close]')).toBeVisible();
      expect(await page.locator('[data-composer-close]').evaluate(close => document.elementFromPoint(close.getBoundingClientRect().x + 10,
        close.getBoundingClientRect().y + 10)?.closest('[data-composer-close]') !== null)).toBe(true);
    }
    await text.click();
    await text.pressSequentially('Merci @cam');
    await expect(page.locator('[data-mention-choice]')).toHaveCount(2);
    const audit = await new AxeBuilder({ page }).include('[data-composer]').include('[data-mention-list]')
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await text.press('ArrowDown');
    await text.press('Enter');
    await expect(text).toHaveValue('Merci @cam_d ');
    expect(asked.at(-1)).toBe('cam');

    await text.pressSequentially('sur #kaf');
    await page.locator('[data-mention-choice="kafka-streams"]').click();
    await expect(text).toHaveValue('Merci @cam_d sur #kafka-streams ');
    await expect(page.locator('[data-mention-list]')).toHaveCount(0);
    await expect(page.locator('[data-publish]')).toBeEnabled();
  });
}
