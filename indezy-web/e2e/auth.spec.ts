import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

/** BCrypt hashes at most 72 bytes: 36 accented letters fit, 37 (74 bytes) do not, although far under 72 characters. */
const LONGEST_PASSWORD = 'é'.repeat(36);
const TOO_LONG_PASSWORD = 'é'.repeat(37);
const text = {
  en: {
    tooLong: 'Maximum 72 bytes (é = 2, emoji = 4)', firstName: 'First name', lastName: 'Last name', email: 'Email',
    password: 'Password', confirmPassword: 'Confirm password', register: 'Create my account',
    registerError: 'Error creating account. Please try again.', emailExists: 'An account already exists with this email.',
    security: 'Security', currentPassword: 'Current password', newPassword: 'New password',
    changePassword: 'Change password', passwordChanged: 'Password changed successfully'
  },
  fr: {
    tooLong: 'Maximum 72 octets (é = 2, emoji = 4)', firstName: 'Prénom', lastName: 'Nom', email: 'Email',
    password: 'Mot de passe', confirmPassword: 'Confirmer le mot de passe', register: 'Créer mon compte',
    registerError: 'Erreur lors de la création du compte. Veuillez réessayer.', emailExists: 'Un compte existe déjà avec cet email.',
    security: 'Sécurité', currentPassword: 'Mot de passe actuel', newPassword: 'Nouveau mot de passe',
    changePassword: 'Changer le mot de passe', passwordChanged: 'Mot de passe changé avec succès'
  }
};

test('presents the public authentication routes', async ({ page }) => {
  await page.goto('/login');
  await expect(page).toHaveTitle(/Indezy/);
  await expect(page.locator('form.login-form')).toBeVisible();
  await expect(page.locator('input[autocomplete="email"]')).toBeVisible();
  await expect(page.locator('a[href="/register"]')).toBeVisible();

  await page.locator('a[href="/register"]').click();
  await expect(page).toHaveURL(/\/register$/);
  await expect(page.locator('form.register-form')).toBeVisible();
  await expect(page.locator('input[autocomplete="given-name"]')).toBeVisible();
});

for (const language of ['en', 'fr'] as const) {
  const t = text[language];

  test(`limits the registration password to 72 bytes and keeps other rejections apart from existing accounts (${language})`,
    async ({ page }) => {
      await page.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
      const registeredPasswords: string[] = [];
      await page.route('**/api/auth/register', async route => {
        registeredPasswords.push(route.request().postDataJSON().password);
        await route.fulfill({ status: 400, json: { status: 400, error: 'Validation Failed' } });
      });

      await page.goto('/register');
      const password = page.getByLabel(t.password, { exact: true });
      const confirmPassword = page.getByLabel(t.confirmPassword, { exact: true });
      const submit = page.getByRole('button', { name: t.register });
      await page.getByLabel(t.firstName, { exact: true }).fill('Jean');
      await page.getByLabel(t.lastName, { exact: true }).fill('Dupont');
      await page.getByLabel(t.email, { exact: true }).fill('jean.dupont@example.test');
      await password.fill(TOO_LONG_PASSWORD);
      await confirmPassword.fill(TOO_LONG_PASSWORD);
      await expect(page.getByText(t.tooLong)).toBeVisible();
      await expect(submit).toBeDisabled();

      await password.fill(LONGEST_PASSWORD);
      await confirmPassword.fill(LONGEST_PASSWORD);
      await expect(page.getByText(t.tooLong)).toBeHidden();
      await submit.click();
      // A rejection that is not a conflict no longer reads as an existing account.
      await expect(page.getByText(t.registerError)).toBeVisible();
      await expect(page.getByText(t.emailExists)).toBeHidden();
      expect(registeredPasswords).toEqual([LONGEST_PASSWORD]);
    });

  test(`limits the new password of a password change to 72 bytes (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/profile', route => route.fulfill({ json: {
      id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test'
    } }));
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language, inactivityDays: 14 } }));
    await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
    const newPasswords: string[] = [];
    await page.route('**/api/users/change-password', async route => {
      newPasswords.push(route.request().postDataJSON().newPassword);
      await route.fulfill({ json: true });
    });

    await page.goto('/profile');
    await page.getByRole('tab', { name: t.security }).click();
    const newPassword = page.getByLabel(t.newPassword, { exact: true });
    const confirmPassword = page.getByLabel(t.confirmPassword, { exact: true });
    const submit = page.getByRole('button', { name: t.changePassword });
    await page.getByLabel(t.currentPassword, { exact: true }).fill('Password123!');
    await newPassword.fill(TOO_LONG_PASSWORD);
    await confirmPassword.fill(TOO_LONG_PASSWORD);
    await expect(page.getByText(t.tooLong)).toBeVisible();
    await expect(submit).toBeDisabled();

    await newPassword.fill(LONGEST_PASSWORD);
    await confirmPassword.fill(LONGEST_PASSWORD);
    await expect(page.getByText(t.tooLong)).toBeHidden();
    await submit.click();
    await expect(page.getByText(t.passwordChanged)).toBeVisible();
    expect(newPasswords).toEqual([LONGEST_PASSWORD]);
  });
}
