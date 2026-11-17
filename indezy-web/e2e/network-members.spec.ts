import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession, themeByWidth } from './helpers/workspace';

const member = (handle: string, firstName: string) => ({ handle, firstName, lastName: 'Martin', avatar: null, headline: 'Data engineer',
  stack: ['Kafka', 'Java'], city: 'Lyon', remote: true, availability: 'LISTENING', availabilityDate: null, languages: [], verified: false });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`reads the members followed, the followers and the requests in tabs, writing to those followed (${language}, ${width}px)`,
    async ({ page }) => {
      const en = language === 'en';
      await page.setViewportSize({ width, height: 900 });
      await mockWorkspaceSession(page, language);
      await themeByWidth(page, language, width);
      await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
      await page.route('**/api/network/conversations/unread', route => route.fulfill({ json: { conversations: 0 } }));
      await page.route('**/api/network/notifications/unread', route => route.fulfill({ json: { unread: 0 } }));
      await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille' } }));
      await page.route('**/api/network/members?**', route => route.fulfill({ json: { members: [member('leo_m', 'Léo'), member('ines_b', 'Inès')],
        total: 2, page: 0, size: 20, followed: ['leo_m'], requested: [], privateCards: [] } }));
      await page.route('**/api/network/members/camille/following', route => route.fulfill({ json: [member('leo_m', 'Léo')] }));
      await page.route('**/api/network/members/camille/followers', route => route.fulfill({ json: [member('ines_b', 'Inès')] }));
      let waiting = [member('zoe_p', 'Zoé')];
      await page.route('**/api/network/follow-requests', route => route.fulfill({ json: waiting }));
      await page.route('**/api/network/follow-requests/sent', route => route.fulfill({ json: [member('paul_d', 'Paul')] }));
      await page.route('**/api/network/follow-requests/zoe_p', route => {
        waiting = [];
        return route.fulfill({ json: [] });
      });
      await page.route('**/api/network/conversations', route => route.fulfill({ json: { id: 4, with: member('leo_m', 'Léo'), last: null,
        unread: 0, muted: false, archived: false, closed: null, lastMessageAt: null } }));
      await page.route('**/api/network/conversations?**', route => route.fulfill({ json: [] }));
      await page.route('**/api/network/conversations/4**', route => route.fulfill({ json: { conversation: { id: 4, with: member('leo_m', 'Léo'),
        last: null, unread: 0, muted: false, archived: false, closed: null, lastMessageAt: null }, more: false, messages: [] } }));

      await page.goto('/network/members');
      // Opened first, Back goes to the feed.
      await expect(page.locator('[data-network-back]')).toBeVisible();
      const tab = (name: string) => page.getByRole('tab', { name, exact: false });
      await expect(page.getByRole('tab')).toHaveText(en ? ['All members', 'Following', 'Followers', 'Requests received 1', 'Requests sent']
        : ['Tous les membres', 'Abonnements', 'Abonnés', 'Demandes reçues 1', 'Demandes envoyées']);
      await expect(page.locator('[data-directory-message="leo_m"]')).toBeVisible();
      await expect(page.locator('[data-directory-message="ines_b"]')).toHaveCount(0);

      await tab(en ? 'Followers' : 'Abonnés').click();
      await expect(page).toHaveURL(/\/network\/members\?tab=followers$/);
      // Ines follows the reader: she can be written to though the reader does not follow her back.
      await expect(page.locator('[data-members-list="followers"] [data-directory-message="ines_b"]')).toBeVisible();
      await expect(page.locator('[data-directory-follow="ines_b"]')).toHaveText(en ? 'Follow' : 'Suivre');

      await tab(en ? 'Requests received' : 'Demandes reçues').click();
      await expect(page.locator('[data-approve="zoe_p"]')).toBeVisible();
      const audit = await new AxeBuilder({ page }).include('app-member-directory').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
      expect(audit.violations.map(violation => violation.id)).toEqual([]);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
      await page.screenshot({ path: test.info().outputPath(`members-requests-${width}.png`), fullPage: true });
      await page.locator('[data-approve="zoe_p"]').click();
      await expect(page.locator('[data-members-empty="received"]')).toBeVisible();
      await expect(tab(en ? 'Requests received' : 'Demandes reçues')).toHaveText(en ? 'Requests received' : 'Demandes reçues');

      await tab(en ? 'Requests sent' : 'Demandes envoyées').click();
      await expect(page.locator('[data-directory-follow="paul_d"]')).toHaveText(en ? 'Request sent' : 'Demande envoyée');

      await tab(en ? 'Following' : 'Abonnements').click();
      await expect(page.locator('[data-members-list="following"] [data-directory-tile="leo_m"]')).toBeVisible();
      await page.waitForTimeout(400);
      await page.screenshot({ path: test.info().outputPath(`members-following-${width}.png`), fullPage: true });
      await page.locator('[data-directory-message="leo_m"]').click();
      await expect(page).toHaveURL(/\/network\/messages\/4$/);
    });
}
