import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const hook = { id: 3, url: 'https://hooks.example.com/indezy', events: ['INVOICE_ISSUED', 'INVOICE_PAID'], active: true,
  consecutiveFailures: 0, lastDeliveryAt: null, lastStatus: null, createdAt: '2026-10-27T10:00:00' };

for (const width of [1280, 390]) {
  test(`adds a webhook, keeps its secret once and sends a test (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    const calls: unknown[] = [];
    await page.route('**/api/users/webhooks', route => {
      if (route.request().method() === 'POST') {
        calls.push(route.request().postDataJSON());
        return route.fulfill({ status: 201, json: { webhook: hook, secret: 'f'.repeat(64) } });
      }
      return route.fulfill({ json: [] });
    });
    await page.route('**/api/users/webhooks/3/ping', route => {
      calls.push('ping');
      return route.fulfill({ status: 202, json: { id: 7, event: 'PING', status: 'PENDING', attempts: 0 } });
    });

    await page.goto('/profile');
    await page.getByRole('tab', { name: 'Security' }).click();
    const card = page.locator('[data-webhooks]');
    // A field in error stays readable: its red text meets the contrast WCAG AA asks for.
    await card.getByLabel('HTTPS address').fill('http://hooks.example.com/indezy');
    await card.getByLabel('HTTPS address').blur();
    await expect(card.locator('mat-error')).toHaveText('An https:// address');
    const errorAudit = await new AxeBuilder({ page }).include('[data-webhooks]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(errorAudit.violations.map(violation => violation.id)).toEqual([]);
    await card.getByLabel('HTTPS address').fill('https://hooks.example.com/indezy');
    await card.locator('[data-event="OPPORTUNITY_STATUS_CHANGED"]').getByRole('checkbox').uncheck();
    await card.locator('[data-event="INVOICE_ISSUED"]').getByRole('checkbox').check();
    await card.locator('[data-add-webhook]').click();
    await expect(card.locator('[data-webhook-secret]')).toHaveText('f'.repeat(64));
    await expect(card.locator('[data-webhook="3"]')).toContainText('Invoice issued');
    const audit = await new AxeBuilder({ page }).include('[data-webhooks]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await card.getByRole('button', { name: 'Done' }).click();
    await expect(card.locator('[data-webhook-secret]')).toHaveCount(0);
    await card.locator('[data-webhook="3"] [data-ping]').click();
    await expect(page.getByText('Test queued')).toBeVisible();

    expect(calls).toEqual([{ url: 'https://hooks.example.com/indezy', events: ['INVOICE_PAID', 'INVOICE_ISSUED'] }, 'ping']);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
