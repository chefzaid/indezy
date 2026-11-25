import { expect, test } from '@playwright/test';
import { wcagViolations } from './helpers/axe';
import { mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr'] as const) {
  const en = language === 'en';
  test(`corrects the AI's sort of an answer and its day to write back (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    await mockWorkspaceSession(page, language);
    const contact = { id: 3, firstName: 'Claire', lastName: 'Martin', clientId: 1, clientName: 'Acme', freelanceId: 1, doNotContact: true };
    const sent = { id: 1, contactId: 3, occurredOn: '2026-11-20', channel: 'EMAIL', direction: 'OUTGOING', replied: true, summary: 'Mission Java' };
    let answer: Record<string, unknown> = { id: 2, contactId: 3, occurredOn: '2026-11-22', channel: 'EMAIL', direction: 'INCOMING',
      replied: null, summary: 'Re: Mission Java', replyKind: 'DO_NOT_CONTACT', writeBackOn: null };
    await page.route('**/api/contacts/3', route => route.fulfill({ json: contact }));
    await page.route('**/api/contacts/3/interactions', route => route.fulfill({ json: { interactions: [answer, sent],
      lastContactedOn: '2026-11-22', asked: 1, answered: 1, responseRate: 100, preferredChannel: 'EMAIL' } }));
    const sorts: unknown[] = [];
    await page.route('**/api/contacts/3/interactions/2/answer', route => {
      const body = route.request().postDataJSON() as { replyKind: string; writeBackOn: string | null };
      sorts.push(body);
      answer = { ...answer, replyKind: body.replyKind, writeBackOn: body.replyKind === 'NOT_NOW' ? body.writeBackOn ?? '2027-02-25' : null };
      return route.fulfill({ json: answer });
    });

    await page.goto('/contacts/3');
    await expect(page.locator('[data-do-not-contact]')).toBeVisible();
    await expect(page.locator('[data-reply-kind="DO_NOT_CONTACT"]')).toBeVisible();
    await expect(page.locator('[data-sort-answer="1"]')).toHaveCount(0);

    // "Do not contact" was wrong: "not now", written back to three months later, the contact reachable again.
    await page.getByRole('button', { name: en ? 'Sort the answer of' : 'Classer la réponse du' }).click();
    await expect(page.getByRole('menuitemradio', { name: en ? 'Do not contact' : 'Ne plus contacter' })).toHaveAttribute('aria-checked', 'true');
    await page.waitForTimeout(300);
    expect(await wcagViolations(page), 'sort menu').toEqual([]);
    await page.getByRole('menuitemradio', { name: en ? 'Not now' : 'Pas maintenant' }).click();
    await expect(page.locator('[data-reply-kind="NOT_NOW"]')).toBeVisible();
    await expect(page.locator('[data-do-not-contact]')).toHaveCount(0);
    const day = page.getByLabel(en ? 'Write back on' : 'Recontacter le');
    await expect(day).toHaveValue('2027-02-25');
    await day.fill('2027-01-15');
    await expect(page.locator('[data-write-back-on]')).toContainText('2027');
    expect(sorts).toEqual([{ replyKind: 'NOT_NOW', writeBackOn: null }, { replyKind: 'NOT_NOW', writeBackOn: '2027-01-15' }]);
    await page.waitForLoadState('networkidle');
    expect(await wcagViolations(page), 'contact with a sorted answer').toEqual([]);
  });
}
