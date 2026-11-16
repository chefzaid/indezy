import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

const leo = { handle: 'leo_m', firstName: 'Léo', lastName: 'Martin', avatar: null, headline: 'Data engineer', stack: ['Kafka'],
  city: 'Nantes', remote: true, availability: null, availabilityDate: null, languages: [], verified: false };
const conversation = { id: 4, with: leo, last: { mine: false, text: 'Une mission Kafka ?', deleted: false, sentAt: '2026-11-16T09:00:00' },
  unread: 1, muted: false, archived: false, closed: null, lastMessageAt: '2026-11-16T09:00:00' };
const message = (id: number, mine: boolean, text: string, extra: Record<string, unknown> = {}) => ({ id, mine, text,
  sentAt: `2026-11-16T09:0${id}:00`, edited: false, deleted: false, editable: mine, replyTo: null, reactions: [], myReaction: null, ...extra });

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`writes to a member from their page, replies and reacts in the conversation (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/members/leo_m/page', route => route.fulfill({ json: { card: leo, followers: 4, following: 2,
      sharedSkills: [], followed: true, followsYou: true, own: false, muted: false, blocked: false, privateCard: false, requested: false } }));
    await page.route('**/api/network/members/leo_m/posts?**', route => route.fulfill({ json: { posts: [], page: 0, more: false } }));
    await page.route('**/api/network/conversations/unread', route => route.fulfill({ json: { conversations: 1 } }));
    const sent: Record<string, unknown>[] = [];
    await page.route('**/api/network/conversations', route => route.fulfill({ json: conversation }));
    await page.route('**/api/network/conversations?**', route => route.fulfill({ json: [conversation] }));
    await page.route('**/api/network/conversations/4', route => route.fulfill({ json: { conversation, more: false,
      messages: [message(1, false, 'Une mission Kafka ?', { card: { role: 'Lead Kafka', dailyRate: 720, city: 'Lyon', startDate: null,
        remoteDaysPerMonth: null, stack: ['Kafka'], takenProjectId: null } })] } }));
    await page.route('**/api/network/conversations/4/read', route => route.fulfill({ json: { ...conversation, unread: 0 } }));
    await page.route('**/api/network/conversations/4/messages', route => {
      const body = route.request().postDataJSON();
      sent.push(body);
      return route.fulfill({ status: 201, json: message(2, true, body.text, { replyTo: { id: 1, mine: false, excerpt: 'Une mission Kafka ?',
        deleted: false } }) });
    });
    await page.route('**/api/network/messages/1/reaction**', route => route.fulfill({ json: message(1, false, 'Une mission Kafka ?',
      { reactions: [{ reaction: 'BRAVO', count: 1 }], myReaction: 'BRAVO' }) }));

    await page.goto('/m/leo_m');
    await page.locator('[data-message]').click();
    await expect(page).toHaveURL(/\/network\/messages\/4$/);
    const thread = page.locator('app-message-thread');
    await expect(thread.locator('[data-message="1"]')).toContainText('Une mission Kafka ?');
    if (width >= 900) {
      await expect(page.locator('[data-conversation="4"]')).toContainText('Léo Martin');
    } else {
      await expect(page.locator('[data-conversation="4"]')).toBeHidden();
    }

    await thread.locator('[data-message="1"]').hover();
    await thread.locator('[data-message="1"] [data-message-reply]').click();
    await expect(thread.locator('[data-thread-context]')).toContainText('Une mission Kafka ?');
    await thread.locator('[data-thread-text]').fill('Avec plaisir');
    await thread.locator('[data-thread-text]').press('Enter');
    await expect(thread.locator('[data-message="2"]')).toContainText('Avec plaisir');
    await expect(thread.locator('[data-message="2"] [data-message-quote]')).toContainText('Une mission Kafka ?');
    expect(sent).toEqual([{ text: 'Avec plaisir', replyTo: 1, opportunityId: null }]);

    await thread.locator('[data-message="1"]').hover();
    await thread.locator('[data-message="1"] [data-message-react]').click();
    await page.locator('[data-message-reaction="BRAVO"]').click();
    await expect(thread.locator('[data-message="1"] [data-message-reactions]')).toContainText('👏 1');

    // An opportunity of the reader's goes as a card; Léo's card goes to the reader's pipeline in one click.
    await page.route('**/api/projects/by-freelance/**', route => route.fulfill({ json: [{ id: 12, role: 'Lead Java', dailyRate: 650 }] }));
    await page.route('**/api/network/messages/1/pipeline', route => route.fulfill({ status: 201, json: { id: 77, role: 'Lead Kafka' } }));
    await thread.locator('[data-thread-share]').click();
    await page.locator('[data-share-opportunity="12"]').click();
    expect(sent.at(-1)).toEqual({ text: '', replyTo: null, opportunityId: 12 });

    // Another member's message is reported to the moderators with a reason.
    let reported: unknown = null;
    await page.route('**/api/network/messages/1/report', route => {
      reported = route.request().postDataJSON();
      return route.fulfill({ status: 204 });
    });
    await thread.locator('[data-message="1"]').hover();
    await thread.locator('[data-message="1"] [data-message-report]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(en ? 'Report this message' : 'Signaler ce message');
    await dialog.locator('[data-report-reason]').click();
    await page.locator('[data-report-option="SPAM"]').click();
    await dialog.locator('[data-report-send]').click();
    await expect(dialog).toHaveCount(0);
    expect(reported).toEqual({ reason: 'SPAM', details: null });

    await thread.locator('[data-message="1"] [data-card-take]').click();
    await expect(thread.locator('[data-message="1"] [data-card-open]')).toHaveAttribute('href', '/projects/77');

    const audit = await new AxeBuilder({ page }).include('[data-messages-page]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await expect(thread.locator('[data-thread-text]')).toHaveAttribute('placeholder', en ? 'Write to Léo…' : 'Écrire à Léo…');
  });
}

test('creates a group among the followers and writes in it, each message showing who wrote it', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
  await page.route('**/api/network/conversations/unread', route => route.fulfill({ json: { conversations: 0 } }));
  await page.route('**/api/network/membership', route => route.fulfill({ json: { joined: true, handle: 'camille' } }));
  const ines = { ...leo, handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard' };
  await page.route('**/api/network/members/camille/followers', route => route.fulfill({ json: [leo, ines] }));
  const group = { id: 9, with: null, group: { title: 'Mission Kafka', members: [leo, ines], size: 3, creator: 'camille', admin: true },
    last: null, unread: 0, muted: false, archived: false, closed: null, lastMessageAt: null };
  let created: unknown = null;
  await page.route('**/api/network/groups', route => {
    created = route.request().postDataJSON();
    return route.fulfill({ json: group });
  });
  await page.route('**/api/network/conversations?**', route => route.fulfill({ json: created ? [group] : [] }));
  await page.route('**/api/network/conversations/9', route => route.fulfill({ json: { conversation: group, more: false,
    messages: [message(1, false, 'Bonjour à tous', { from: ines })] } }));
  await page.route('**/api/network/conversations/9/read', route => route.fulfill({ json: group }));

  await page.goto('/network/messages');
  await page.locator('[data-messages-new-group]').click();
  const dialog = page.getByRole('dialog');
  await dialog.locator('[data-group-title]').fill('Mission Kafka');
  await dialog.locator('[data-group-choice="leo_m"]').click();
  await dialog.locator('[data-group-choice="ines_b"]').click();
  const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await dialog.locator('[data-group-done]').click();
  await expect(page).toHaveURL(/\/network\/messages\/9$/);
  expect(created).toEqual({ title: 'Mission Kafka', handles: ['leo_m', 'ines_b'] });
  await expect(page.locator('[data-thread-group]')).toContainText('3 members');
  await expect(page.locator('[data-message="1"] [data-message-sender]')).toContainText('Inès Bernard');
});
