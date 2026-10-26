import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

for (const language of ['en', 'fr']) {
  test(`keeps free skills with their level and years on the profile (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    const en = language === 'en';
    if (!en) {
      await page.setViewportSize({ width: 390, height: 844 });
    }
    await mockWorkspaceSession(page, language);
    let profile: Record<string, unknown> = {
      id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test',
      skills: ['Java'], skillDetails: { Java: { level: 'EXPERT', years: 10 } }
    };
    await page.route('**/api/users/profile', async route => {
      if (route.request().method() === 'PUT') {
        profile = { ...profile, ...route.request().postDataJSON() };
      }
      await route.fulfill({ json: profile });
    });
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language, inactivityDays: 14 } }));
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));

    await page.goto('/profile');
    const section = page.locator('.skills-section');
    const rows = section.locator('.skill-row');
    await expect(rows).toHaveCount(1);
    await expect(rows.first().getByRole('spinbutton')).toHaveValue('10');
    await expect(rows.first().getByRole('combobox')).toContainText('Expert');

    const add = section.getByRole('combobox', { name: en ? 'Add a skill' : 'Ajouter une compétence' });
    await add.fill('Kub');
    await page.getByRole('option', { name: 'Kubernetes', exact: true }).click();
    await add.fill('  Event Sourcing ');
    await add.press('Enter');
    await add.fill('java');
    await add.press('Enter');
    await expect(rows).toHaveCount(3);
    await expect(section.locator('.skill-name')).toHaveText(['Java', 'Kubernetes', 'Event Sourcing']);

    const sourcing = rows.nth(2);
    await sourcing.getByRole('combobox').click();
    await page.getByRole('option', { name: en ? 'Advanced' : 'Avancé', exact: true }).click();
    await expect(page.getByRole('listbox')).toBeHidden();
    await sourcing.getByRole('spinbutton').fill('3');
    await sourcing.getByRole('spinbutton').press('Tab');
    await rows.nth(1).getByRole('button', { name: en ? 'Remove Kubernetes' : 'Retirer Kubernetes' }).click();
    await section.screenshot({ path: test.info().outputPath('skills.png') });

    await page.getByRole('tabpanel').getByRole('button', { name: en ? 'Save' : 'Sauvegarder', exact: true }).click();
    await expect.poll(() => profile['skills']).toEqual(['Java', 'Event Sourcing']);
    expect(profile['skillDetails']).toEqual({ Java: { level: 'EXPERT', years: 10 }, 'Event Sourcing': { level: 'ADVANCED', years: 3 } });

    await page.reload();
    await expect(section.locator('.skill-name')).toHaveText(['Java', 'Event Sourcing']);
    await expect(rows.nth(1).getByRole('spinbutton')).toHaveValue('3');
  });
}
