import { expect, test } from '@playwright/test';
import { wcagViolations } from './helpers/axe';
import { mockWorkspaceSession } from './helpers/workspace';

const first = 'inbox+' + 'a'.repeat(40) + '@replies.example';
const second = 'inbox+' + 'b'.repeat(40) + '@replies.example';

for (const language of ['en', 'fr'] as const) {
  const en = language === 'en';
  test(`gives an address to forward emails to, replaced only once confirmed (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language, inactivityDays: 14 } }));
    await page.route('**/api/users/profile', route => route.fulfill({ json: {
      id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test' } }));
    const recent = [
      { id: 1, sender: 'ines@acme.example', subject: 'Entretien Lead Java', kind: 'INTERVIEW_INVITE', action: 'INTERVIEW_STEP',
        contactId: 3, projectId: 42, receivedAt: '2026-11-24T10:00:00' },
      { id: 2, sender: 'marc@conseil.example', subject: 'Dispo en janvier ?', kind: 'AVAILABILITY_ASK', action: 'DRAFTED',
        contactId: 4, projectId: null, receivedAt: '2026-11-24T09:00:00' }];
    let address: string | null = null;
    const requested: string[] = [];
    await page.route('**/api/ai/inbox-watcher', route => route.fulfill({ json: { available: true, address, recent } }));
    await page.route('**/api/ai/inbox-watcher/address', route => {
      requested.push(route.request().method());
      address = address === null ? first : second;
      return route.fulfill({ json: { available: true, address, recent } });
    });

    await page.goto('/profile');
    await page.getByRole('tab', { name: en ? 'Preferences' : 'Préférences', exact: true }).click();
    const card = page.locator('[data-inbox-watcher]');
    await expect(card).toBeVisible();
    await card.getByRole('button', { name: en ? 'Get my address' : 'Obtenir mon adresse' }).click();
    await expect(card.locator('[data-inbox-address]')).toHaveText(first);

    // The latest emails link their opportunity, else their contact.
    await expect(card.locator('[data-inbox-project="42"]')).toHaveAttribute('href', '/projects/42');
    await expect(card.locator('[data-inbox-contact="4"]')).toHaveAttribute('href', '/contacts/4');
    await page.waitForLoadState('networkidle');
    expect(await wcagViolations(page), 'inbox watcher').toEqual([]);

    // A new address: warned first, kept when cancelled.
    const renew = card.getByRole('button', { name: en ? 'Get a new address' : 'Obtenir une nouvelle adresse' });
    await renew.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toContainText(en ? 'receives nothing' : 'ne reçoit alors plus rien');
    await dialog.getByRole('button', { name: en ? 'Cancel' : 'Annuler' }).click();
    await expect(dialog).toHaveCount(0);
    await expect(card.locator('[data-inbox-address]')).toHaveText(first);
    expect(requested).toEqual(['POST']);

    await renew.click();
    await page.getByRole('dialog').getByRole('button', { name: en ? 'Get a new address' : 'Obtenir une nouvelle adresse' }).click();
    await expect(card.locator('[data-inbox-address]')).toHaveText(second);
    expect(requested).toEqual(['POST', 'POST']);
  });
}
