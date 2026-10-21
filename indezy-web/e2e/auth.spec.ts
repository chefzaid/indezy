import { Page, expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

/** BCrypt hashes at most 72 bytes: 36 accented letters fit, 37 (74 bytes) do not, although far under 72 characters. */
const LONGEST_PASSWORD = 'é'.repeat(36);
const TOO_LONG_PASSWORD = 'é'.repeat(37);
const text = {
  en: {
    tooLong: 'Maximum 72 bytes (é = 2, emoji = 4)', firstName: 'First name', lastName: 'Last name', email: 'Email',
    emailPlaceholder: 'your@email.com',
    password: 'Password', confirmPassword: 'Confirm password', register: 'Create my account',
    hidePassword: 'Hide password', hideConfirmPassword: 'Hide password confirmation',
    registerError: 'Error creating account. Please try again.', emailExists: 'An account already exists with this email.',
    security: 'Security', currentPassword: 'Current password', newPassword: 'New password',
    changePassword: 'Change password', passwordChanged: 'Password changed successfully',
    tooShort: 'Password must be at least 8 characters', changeError: 'Error changing password',
    wrongCurrent: 'The current password is incorrect'
  },
  fr: {
    tooLong: 'Maximum 72 octets (é = 2, emoji = 4)', firstName: 'Prénom', lastName: 'Nom', email: 'Email',
    emailPlaceholder: 'votre@email.com',
    password: 'Mot de passe', confirmPassword: 'Confirmer le mot de passe', register: 'Créer mon compte',
    hidePassword: 'Masquer le mot de passe', hideConfirmPassword: 'Masquer la confirmation du mot de passe',
    registerError: 'Erreur lors de la création du compte. Veuillez réessayer.', emailExists: 'Un compte existe déjà avec cet email.',
    security: 'Sécurité', currentPassword: 'Mot de passe actuel', newPassword: 'Nouveau mot de passe',
    changePassword: 'Changer le mot de passe', passwordChanged: 'Mot de passe changé avec succès',
    tooShort: 'Le mot de passe doit contenir au moins 8 caractères', changeError: 'Erreur lors du changement de mot de passe',
    wrongCurrent: 'Le mot de passe actuel est incorrect'
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

  test(`shows the login email placeholder in the page language (${language})`, async ({ page }) => {
    await page.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
    await page.goto('/login');
    await expect(page.getByLabel(t.email, { exact: true })).toHaveAttribute('placeholder', t.emailPlaceholder);
  });

  test(`names the register password visibility buttons in the page language (${language})`, async ({ page }) => {
    await page.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
    await page.goto('/register');
    const hidePassword = page.getByRole('button', { name: t.hidePassword, exact: true });
    await expect(hidePassword).toHaveAttribute('aria-pressed', 'true');
    await expect(page.getByRole('button', { name: t.hideConfirmPassword, exact: true })).toHaveAttribute('aria-pressed', 'true');

    await hidePassword.click();
    await expect(hidePassword).toHaveAttribute('aria-pressed', 'false');
    await expect(page.getByLabel(t.password, { exact: true })).toHaveAttribute('type', 'text');
  });

  test(`limits the new password of a password change to 72 bytes (${language})`, async ({ page }) => {
    await mockProfilePage(page, language);
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

  test(`requires 8 characters for the new password of a password change and reports a server rejection (${language})`,
    async ({ page }) => {
      await mockProfilePage(page, language);
      const newPasswords: string[] = [];
      await page.route('**/api/users/change-password', async route => {
        newPasswords.push(route.request().postDataJSON().newPassword);
        await route.fulfill({ status: 400, json: {
          status: 400, error: 'Validation Failed', validationErrors: { newPassword: 'New password must be at least 8 characters long' }
        } });
      });

      await page.goto('/profile');
      await page.getByRole('tab', { name: t.security }).click();
      const newPassword = page.getByLabel(t.newPassword, { exact: true });
      const confirmPassword = page.getByLabel(t.confirmPassword, { exact: true });
      const submit = page.getByRole('button', { name: t.changePassword });
      await page.getByLabel(t.currentPassword, { exact: true }).fill('Password123!');
      await newPassword.fill('Abcdef1');
      await confirmPassword.fill('Abcdef1');
      await expect(page.getByText(t.tooShort)).toBeVisible();
      await expect(submit).toBeDisabled();

      // The server enforces the same minimum; were it ever to reject what the form allows, the page says the change failed.
      await newPassword.fill('Abcdef12');
      await confirmPassword.fill('Abcdef12');
      await expect(page.getByText(t.tooShort)).toBeHidden();
      await submit.click();
      await expect(page.getByText(t.changeError)).toBeVisible();
      await expect(page.getByText(t.passwordChanged)).toBeHidden();
      await expect(newPassword).toHaveValue('Abcdef12');
      expect(newPasswords).toEqual(['Abcdef12']);
    });
  test(`tells a wrong current password on its field (${language})`, async ({ page }) => {
    await mockProfilePage(page, language);
    await page.route('**/api/users/change-password', route => route.fulfill({ status: 400, json: {
      status: 400, error: 'Validation Failed', validationErrors: { currentPassword: 'Current password is incorrect' }
    } }));

    await page.goto('/profile');
    await page.getByRole('tab', { name: t.security }).click();
    const current = page.getByLabel(t.currentPassword, { exact: true });
    await current.fill('NotMyPassword1!');
    await page.getByLabel(t.newPassword, { exact: true }).fill('Abcdef12');
    await page.getByLabel(t.confirmPassword, { exact: true }).fill('Abcdef12');
    await page.getByRole('button', { name: t.changePassword }).click();

    await expect(page.getByText(t.wrongCurrent)).toBeVisible();
    await expect(page.getByText(t.changeError)).toBeHidden();
    await expect(current).toHaveAttribute('aria-invalid', 'true');
    // Typing again clears it.
    await current.fill('Password123!');
    await expect(page.getByText(t.wrongCurrent)).toBeHidden();
  });
}

/** The profile page of a signed-in session, with the API calls it makes on load intercepted. */
async function mockProfilePage(page: Page, language: string): Promise<void> {
  await mockWorkspaceSession(page, language);
  await page.route('**/api/users/profile', route => route.fulfill({ json: {
    id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test'
  } }));
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language, inactivityDays: 14 } }));
  await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));
}
