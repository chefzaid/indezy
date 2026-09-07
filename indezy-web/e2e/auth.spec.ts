import { expect, test } from '@playwright/test';

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
