import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const width of [1280, 390]) {
  test(`turns the weekly digest on and the new offers badge off (${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    let settings = { weeklyReports: false, weeklyDigestSections: ['PIPELINE', 'OVERDUE_INVOICES', 'INTERVIEWS', 'FOLLOW_UPS', 'QUIET_OPPORTUNITIES', 'MISSIONS_ENDING', 'OFFERS'], bookingEmails: true,
      newOffersBadge: true, emailsAvailable: true };
    const saved: unknown[] = [];
    await page.route('**/api/offers/searches/alerts', route => route.fulfill({ json: { newOffers: 4 } }));
    await page.route('**/api/users/notifications', route => {
      if (route.request().method() === 'PUT') {
        saved.push(route.request().postDataJSON());
        settings = { ...settings, ...route.request().postDataJSON() };
      }
      return route.fulfill({ json: settings });
    });

    await page.goto('/profile');
    if (width === 1280) {
      await expect(page.locator('[data-nav-badge]')).toHaveText('4');
    }
    await page.getByRole('tab', { name: 'Notifications' }).click();
    const card = page.locator('[data-notifications]');
    await expect(card.getByRole('switch')).toHaveCount(3);
    const audit = await new AxeBuilder({ page }).include('[data-notifications]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await card.getByRole('switch', { name: 'Email me a weekly digest' }).click();
    await expect(page.getByText('Notifications updated successfully')).toBeVisible();
    await card.getByRole('checkbox', { name: 'Opportunities gone quiet' }).uncheck();
    const sectionsAudit = await new AxeBuilder({ page }).include('[data-notifications]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(sectionsAudit.violations.map(violation => violation.id)).toEqual([]);
    await card.getByRole('switch', { name: 'Count new offers next to Offers in the menu' }).click();
    await card.getByRole('switch', { name: 'Email me each call booked' }).click();
    await expect.poll(() => saved).toEqual([{ weeklyReports: true }, { weeklyDigestSections: ['PIPELINE', 'OVERDUE_INVOICES', 'INTERVIEWS',
      'FOLLOW_UPS', 'MISSIONS_ENDING', 'OFFERS'] }, { newOffersBadge: false }, { bookingEmails: false }]);
    await expect(page.locator('[data-nav-badge]')).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('keeps the badge off after signing in when the account turned it off', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  await page.route('**/api/offers/searches/alerts', route => route.fulfill({ json: { newOffers: 4 } }));
  await page.route('**/api/users/notifications', route =>
    route.fulfill({ json: { weeklyReports: true, weeklyDigestSections: ['OFFERS'], bookingEmails: false, newOffersBadge: false,
      emailsAvailable: false } }));

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Notifications' }).click();
  await expect(page.locator('[data-emails-unavailable]')).toBeVisible();
  // The only section kept stays.
  await expect(page.getByRole('checkbox', { name: 'New offers matching your profile' })).toBeDisabled();
  await expect(page.locator('[data-nav-badge]')).toHaveCount(0);
});
