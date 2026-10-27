import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const off = { enabled: false, title: '', durationMinutes: 30, bufferMinutes: 15, minNoticeHours: 24, maxDaysAhead: 21, windows: [] };
const inTwoDays = new Date(Date.now() + 2 * 86_400_000);
inTwoDays.setHours(10, 0, 0, 0);
const call = { id: 6, startsAt: inTwoDays.toISOString(), endsAt: new Date(inTwoDays.getTime() + 1_800_000).toISOString(), name: 'Bob Recruiter',
  email: 'bob@esn.example', company: 'ESN', message: 'Java mission', cancelledAt: null };

for (const width of [1280, 390]) {
  test(`sets up the booking link and cancels a booked call (${width}px)`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 900 });
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await mockWorkspaceSession(page, 'en');
    await mockWorkspaceData(page, 'en');
    const saved: unknown[] = [];
    let calls = [call];
    await page.route('**/api/users/booking-page', route => {
      if (route.request().method() === 'PUT') {
        saved.push(route.request().postDataJSON());
        return route.fulfill({ json: { slug: 'Ab3dEf6hJk9mNp2q', settings: route.request().postDataJSON() } });
      }
      return route.fulfill({ json: { slug: null, settings: off } });
    });
    await page.route('**/api/bookings', route => route.fulfill({ json: calls }));
    await page.route('**/api/bookings/6/cancel', route => {
      calls = [{ ...call, cancelledAt: new Date().toISOString() }];
      return route.fulfill({ json: calls[0] });
    });

    await page.goto(`/calendar?month=${inTwoDays.getFullYear()}-${String(inTwoDays.getMonth() + 1).padStart(2, '0')}`);
    const card = page.locator('[data-booking-link]');
    await expect(card).toContainText('The link is off');
    await expect(card.locator('[data-booking="6"]')).toContainText('Bob Recruiter');
    await card.locator('[data-booking-settings]').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-booking-enabled]').getByRole('switch').click();
    await dialog.locator('[data-booking-title]').fill('Call with Alice');
    await dialog.locator('[data-save-booking]').click();
    await expect(dialog.locator('[data-booking-error]')).toHaveText('Open at least one day.');
    await dialog.getByRole('checkbox', { name: 'Tuesday' }).check();
    await dialog.getByLabel('Tuesday until').fill('12:00');
    await page.locator('.mat-mdc-dialog-container').evaluate(container =>
      Promise.all(container.getAnimations({ subtree: true }).map(animation => animation.finished)));
    const dialogAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(dialogAudit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-save-booking]').click();
    await expect(dialog).toBeHidden();
    expect(saved).toEqual([{ enabled: true, title: 'Call with Alice', durationMinutes: 30, bufferMinutes: 15, minNoticeHours: 24,
      maxDaysAhead: 21, windows: [{ dayOfWeek: 'TUESDAY', start: '09:00', end: '12:00' }] }]);
    await expect(card.locator('[data-booking-url]')).toHaveText(/\/book\/Ab3dEf6hJk9mNp2q$/);
    await card.locator('[data-copy-booking-link]').click();
    expect(await page.evaluate(() => navigator.clipboard.readText())).toMatch(/\/book\/Ab3dEf6hJk9mNp2q$/);

    const audit = await new AxeBuilder({ page }).include('.calendar-container').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await card.getByRole('button', { name: 'Cancel the call with Bob Recruiter' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Cancel the call' }).click();
    await expect(card).toContainText('No call booked yet.');
  });
}

test('books a call without an account, and says when the link does not work', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const booked: unknown[] = [];
  await page.route('**/api/public/booking/*', route => {
    if (!route.request().url().endsWith('/good')) {
      return route.fulfill({ status: 404, json: { message: 'Booking link not found' } });
    }
    if (route.request().method() === 'POST') {
      booked.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: { startsAt: '2026-11-03T09:00:00Z', endsAt: '2026-11-03T09:30:00Z', freelancer: 'Alice Martin' } });
    }
    return route.fulfill({ json: { freelancer: 'Alice Martin', title: 'Appel avec Alice', durationMinutes: 30, timeZone: 'Europe/Paris',
      days: [{ date: '2026-11-03', starts: ['2026-11-03T09:00:00Z', '2026-11-03T09:45:00Z'] }] } });
  });

  await page.goto('/book/good');
  // Without an account the page speaks French until the visitor switches.
  await expect(page.getByRole('heading', { name: 'Appel avec Alice' })).toBeVisible();
  await expect(page.locator('main')).toContainText('Réservez un appel de 30 minutes avec Alice Martin.');
  await page.locator('[data-book-start="2026-11-03T09:00:00Z"]').click();
  await page.getByLabel('Votre nom').fill('Bob Recruiter');
  await page.getByLabel('Votre e-mail').fill('bob@esn.example');
  const audit = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.locator('[data-book-submit]').click();
  await expect(page.locator('[data-book-confirmed]')).toContainText('Alice Martin attend votre appel');
  expect(booked).toEqual([{ start: '2026-11-03T09:00:00Z', name: 'Bob Recruiter', email: 'bob@esn.example', company: null, message: null }]);
  expect(page.url()).not.toContain('login');

  await page.goto('/book/replaced');
  await expect(page.getByRole('heading', { name: 'Ce lien ne fonctionne pas' })).toBeVisible();
});
