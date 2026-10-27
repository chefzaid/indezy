import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const width of [1280, 390]) {
  test(`hides and reorders the dashboard's blocks, and keeps the layout (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let preferences: Record<string, unknown> = { theme: 'light', language: 'en', inactivityDays: 14, followUpDays: 5 };
    const saved: unknown[] = [];
    await page.route('**/api/users/preferences', route => {
      if (route.request().method() === 'PUT') {
        preferences = route.request().postDataJSON();
        saved.push(preferences['dashboardWidgets']);
      }
      return route.fulfill({ json: preferences });
    });
    const blocks = () => page.locator('[data-widget]').evaluateAll(items => items.map(item => item.getAttribute('data-widget')));

    await page.goto('/dashboard');
    await expect(page.locator('[data-widget="STATS"]')).toBeVisible();
    expect(await blocks()).toEqual(['STATS', 'CHARTS', 'REMINDERS', 'OFFERS', 'ANALYTICS', 'RECENT']);

    await page.locator('[data-customize]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.locator('[data-widget]')).toHaveCount(6);
    // Measured once it has faded in: halfway, its text is half transparent.
    await page.locator('.mat-mdc-dialog-container').evaluate(container =>
      Promise.all(container.getAnimations({ subtree: true }).map(animation => animation.finished)));
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.getByRole('checkbox', { name: 'Charts' }).uncheck();
    await dialog.getByRole('button', { name: 'Move Recent opportunities up' }).click();
    await dialog.locator('[data-save-layout]').click();

    await expect(page.getByText('Dashboard layout saved')).toBeVisible();
    expect(await blocks()).toEqual(['STATS', 'REMINDERS', 'OFFERS', 'RECENT', 'ANALYTICS']);
    expect(saved).toHaveLength(1);
    expect((saved[0] as { id: string; visible: boolean }[]).find(widget => widget.id === 'CHARTS')?.visible).toBe(false);

    await page.reload();
    await expect(page.locator('[data-widget="STATS"]')).toBeVisible();
    expect(await blocks()).toEqual(['STATS', 'REMINDERS', 'OFFERS', 'RECENT', 'ANALYTICS']);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
