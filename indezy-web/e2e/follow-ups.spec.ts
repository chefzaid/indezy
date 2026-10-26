import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`reminds to follow up a message left unanswered, after the delay set on the profile (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    let preferences: Record<string, unknown> = { theme: 'light', language, inactivityDays: 14, followUpDays: 5 };
    await page.route('**/api/users/preferences', async route => {
      if (route.request().method() === 'PUT') {
        preferences = { ...preferences, ...route.request().postDataJSON() };
      }
      await route.fulfill({ json: preferences });
    });
    await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, firstName: 'Workspace', email: 'w@example.test' } }));
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
    await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
    await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: { ...emptyDashboardStats,
      followUpsDue: [{ contactId: 7, name: 'Hélène Durand', clientName: 'Société Générale', channel: 'EMAIL', sentOn: '2026-10-15',
        daysWaiting: 11 }] } }));

    await page.goto('/dashboard');
    const card = page.locator('.reminder-card.accent-purple');
    await expect(card).toContainText(en ? 'Follow-ups due' : 'Relances à faire');
    await expect(card).toContainText(en ? 'Email sent 11 days ago, no answer' : 'E-mail envoyé il y a 11 jours, sans réponse');
    await expect(card.getByRole('link', { name: /Hélène Durand/ })).toHaveAttribute('href', '/contacts/7');

    await page.goto('/profile');
    await page.getByRole('tab', { name: en ? 'Preferences' : 'Préférences' }).click();
    const delay = page.getByRole('spinbutton', { name: en ? 'Follow up after (days)' : 'Relancer après (jours)' });
    await expect(delay).toHaveValue('5');
    await delay.fill('8');
    await page.getByRole('tabpanel').getByRole('button', { name: en ? 'Save' : 'Sauvegarder', exact: true }).click();
    await expect.poll(() => preferences['followUpDays']).toBe(8);
  });
}
