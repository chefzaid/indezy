import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`invites a contact to the network from their page (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    const contact = { id: 3, firstName: 'Claire', lastName: 'Martin', email: 'claire@acme.example', clientId: 1, clientName: 'Acme', freelanceId: 1 };
    await page.route('**/api/contacts/3', route => route.fulfill({ json: contact }));
    await page.route('**/api/contacts/3/interactions', route => route.fulfill({ json: { interactions: [], asked: 0, answered: 0 } }));
    await page.route('**/api/network/invitations/contacts/3', route => route.fulfill({ json: route.request().method() === 'POST' ?
      { standing: 'INVITED', invitedAt: '2026-11-20T10:00:00', handle: null } : { standing: 'INVITABLE', invitedAt: null, handle: null } }));

    await page.goto('/contacts/3');
    await page.locator('[data-contact-invite-button]').click();
    await expect(page.getByRole('dialog')).toContainText(en ? 'Email Claire Martin an invitation' : 'Envoyer à Claire Martin un e-mail');
    await page.getByRole('button', { name: en ? 'Send the invitation' : 'Envoyer l’invitation' }).click();
    await expect(page.locator('[data-contact-invited]')).toContainText(en ? 'Invited to the network on' : 'Invité sur le réseau le');
  });
}

test('reads an invitation without an account and declines every invitation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  let declined = false;
  await page.route('**/api/public/network-invitations/tok', route => route.fulfill({ json: { inviter: 'Alice Martin', declined } }));
  await page.route('**/api/public/network-invitations/tok/decline', route => {
    declined = true;
    return route.fulfill({ json: { inviter: 'Alice Martin', declined: true } });
  });
  await page.goto('/network-invitation#tok');
  await expect(page.locator('h1')).toContainText('Alice Martin');
  const audit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await page.locator('[data-invitation-decline]').click();
  await expect(page.locator('[data-invitation-declined]')).toBeVisible();
  expect(declined).toBe(true);
});
