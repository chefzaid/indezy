import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';

test('the burger folds the side menu to its icons, each name on hover, and unfolds it', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 800 });
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.goto('/dashboard');
  const menu = page.locator('mat-sidenav');
  await expect(menu).toBeVisible();
  const wide = (await menu.boundingBox())!.width;
  await page.locator('[data-menu-toggle]').click();
  await expect(page.locator('[data-menu-toggle]')).toHaveAttribute('aria-expanded', 'false');
  await expect.poll(async () => (await menu.boundingBox())!.width).toBeLessThan(100);
  await expect(menu).toBeVisible();
  await expect(menu.locator('a[href="/projects"] mat-icon')).toBeVisible();
  await expect(menu.getByRole('link', { name: 'Opportunities' })).toBeVisible();
  await menu.locator('a[href="/projects"]').hover();
  await expect(page.locator('.mat-mdc-tooltip')).toHaveText('Opportunities');
  await page.screenshot({ path: test.info().outputPath('rail.png') });
  const audit = await new AxeBuilder({ page }).include('mat-sidenav').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);

  // Remembered on the next visit, then unfolded again.
  await page.reload();
  await expect.poll(async () => (await menu.boundingBox())!.width).toBeLessThan(100);
  await page.locator('[data-menu-toggle]').click();
  await expect.poll(async () => (await menu.boundingBox())!.width).toBe(wide);
  await expect(page.locator('[data-menu-toggle]')).toHaveAttribute('aria-expanded', 'true');
});

test('on a phone the burger still opens the menu as a drawer', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 });
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.goto('/dashboard');
  await expect(page.locator('mat-sidenav')).toBeHidden();
  await page.locator('[data-menu-toggle]').click();
  await expect(page.locator('mat-sidenav')).toBeVisible();
  await expect(page.locator('mat-sidenav').getByText('Opportunities')).toBeVisible();
});
