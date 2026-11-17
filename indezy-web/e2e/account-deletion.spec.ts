import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession, themeByWidth } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`deletes the account after 30 days on hold, and restores it by signing in (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await themeByWidth(page, language, width);
    const asked: unknown[] = [];
    await page.route('**/api/users/me/deletion', route => {
      asked.push(route.request().postDataJSON());
      return route.fulfill({ status: 202, json: { purgeOn: '2026-12-17' } });
    });
    const signIns: Record<string, unknown>[] = [];
    await page.route('**/api/auth/login', route => {
      const body = route.request().postDataJSON();
      signIns.push(body);
      return body.restore
        ? route.fulfill({ json: { token: 'header.eyJleHAiOjk5OTk5OTk5OTl9.restored', user: { id: 1, email: 'workspace@example.test',
          firstName: 'Workspace', lastName: 'Test' } } })
        : route.fulfill({ status: 409, json: { accountOnHold: true, purgeOn: '2026-12-17' } });
    });

    await page.goto('/profile');
    await page.getByRole('tab', { name: en ? 'Account' : 'Compte' }).click();
    await page.locator('[data-account-delete]').click();
    const confirm = page.locator('[data-account-delete-confirm]');
    await expect(confirm).toContainText(en ? 'Messages you sent stay with the members you wrote to' : 'Les messages que vous avez envoyés restent');
    await expect(page.locator('[data-account-delete-submit]')).toBeDisabled();
    await page.locator('[data-account-delete-email]').fill('workspace@example.test');
    const audit = await new AxeBuilder({ page }).include('[data-account-deletion]').include('[data-account-delete-confirm]')
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('[data-account-delete-submit]').click();
    expect(asked).toEqual([{ confirmation: 'workspace@example.test' }]);

    // Signed out, on the sign-in page, which tells of the hold.
    await expect(page).toHaveURL(/\/login\?onHold=2026-12-17$/);
    const hold = page.locator('[data-account-on-hold]');
    await expect(hold).toContainText(en ? 'This account is on hold' : 'Ce compte est suspendu');
    await expect(hold).toContainText(en ? 'December 17, 2026' : '17 décembre 2026');
    await page.locator('input[formControlName="email"]').fill('workspace@example.test');
    await page.locator('input[formControlName="password"]').fill('Password123!');
    await page.locator('[data-restore-account]').click();
    await expect(page).toHaveURL(/\/dashboard$/);
    expect(signIns.at(-1)).toEqual(expect.objectContaining({ email: 'workspace@example.test', restore: true }));
    const holdAudit = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(holdAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
