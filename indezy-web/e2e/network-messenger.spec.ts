import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession, themeByWidth } from './helpers/workspace';

const leo = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: 'Data engineer', stack: ['Kafka'],
  city: 'Nantes', remote: true, availability: null, availabilityDate: null, languages: [], verified: false };
const ines = { ...leo, handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard' };
const conversation = (id: number, other: typeof leo, extra: Record<string, unknown> = {}) => ({ id, with: other, group: null,
  last: { mine: false, text: `Bonjour, une mission ${id} ?`, deleted: false, sentAt: '2026-11-18T09:00:00' }, unread: 0, muted: false,
  archived: false, closed: null, lastMessageAt: `2026-11-18T09:0${id}:00`, online: false, ...extra });
const message = (id: number, mine: boolean, text: string) => ({ id, mine, text, sentAt: `2026-11-18T09:0${id}:00`, edited: false,
  deleted: false, editable: mine, replyTo: null, reactions: [], myReaction: null, preview: null });

for (const [language, width] of [['en', 1440], ['fr', 1100]] as const) {
  test(`chats from the messenger corner on any page, seeing who is online (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 860 });
    await mockWorkspaceSession(page, language);
    await themeByWidth(page, language, 1280);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
    await page.route('**/api/network/conversations/unread', route => route.fulfill({ json: { conversations: 1 } }));
    await page.route('**/api/network/notifications/unread', route => route.fulfill({ json: { unread: 0 } }));
    await page.route('**/api/network/conversations?**', route => route.fulfill({ json: [conversation(4, leo, { unread: 1, online: true }),
      conversation(5, ines)] }));
    await page.route('**/api/network/conversations/4', route => route.fulfill({ json: { conversation: conversation(4, leo, { online: true }),
      more: false, messages: [message(1, false, 'Bonjour, une mission 4 ?')] } }));
    await page.route('**/api/network/conversations/4/read', route => route.fulfill({ json: conversation(4, leo, { online: true }) }));
    const sent: string[] = [];
    await page.route('**/api/network/conversations/4/messages', route => {
      sent.push(route.request().postDataJSON().text);
      return route.fulfill({ status: 201, json: message(2, true, route.request().postDataJSON().text) });
    });

    await page.goto('/dashboard');
    const dock = page.locator('[data-messenger-dock]');
    await expect(dock.locator('[data-dock-unread]')).toHaveText('1');
    await dock.getByRole('button', { name: en ? 'Messaging' : 'Messagerie' }).click();
    await expect(dock.locator('[data-dock-toggle]')).toHaveAttribute('aria-expanded', 'true');
    const list = dock.locator('[data-dock-list]');
    await expect(list.locator('[data-dock-conversation]')).toHaveCount(2);
    await expect(list.locator('[data-dock-conversation="4"]').getByRole('img', { name: en ? 'Online' : 'En ligne' })).toBeVisible();
    await expect(list.locator('[data-dock-conversation="5"] .online-dot')).toHaveCount(0);

    await list.locator('[data-dock-conversation="4"]').click();
    const chat = dock.locator('[data-dock-chat="4"]');
    await expect(chat).toContainText(en ? 'Online' : 'En ligne');
    await expect(chat.locator('[data-message="1"]')).toContainText('Bonjour, une mission 4 ?');
    // The window says who it is with: the conversation's own head stays hidden.
    await expect(chat.locator('.thread-head')).toBeHidden();
    const field = chat.locator('textarea');
    await field.fill('Avec plaisir, parlons-en');
    await field.press('Enter');
    await expect.poll(() => sent).toEqual(['Avec plaisir, parlons-en']);
    await expect(chat.locator('[data-message="2"]')).toContainText('Avec plaisir, parlons-en');
    const audit = await new AxeBuilder({ page }).include('[data-messenger-dock]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.screenshot({ path: test.info().outputPath(`messenger-${width}.png`) });

    // Folded to its head, then closed; on the Messages page the corner gives way to the page.
    await chat.locator('[data-dock-fold]').click();
    await expect(chat.locator('[data-dock-fold]')).toHaveAttribute('aria-expanded', 'false');
    await expect(chat.locator('app-message-thread')).toHaveCount(0);
    await chat.getByRole('button', { name: en ? 'Close the conversation' : 'Fermer la conversation' }).click();
    await expect(chat).toHaveCount(0);
    await dock.locator('[data-dock-all]').click();
    await expect(page).toHaveURL(/\/network\/messages$/);
    await expect(page.locator('[data-messenger-dock]')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('keeps the messenger corner off a phone, where Messages is a page of its own', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await mockWorkspaceSession(page, 'fr');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.route('**/api/network/conversations?**', route => route.fulfill({ json: [conversation(4, leo)] }));
  await page.goto('/dashboard');
  await expect(page.locator('h1').first()).toBeVisible();
  await expect(page.locator('[data-messenger-dock]')).toHaveCount(0);
});
