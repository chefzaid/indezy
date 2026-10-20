import { expect, test } from '@playwright/test';
import { mockFeed, offer, settle } from './helpers/offer-feed';

test('hides a publisher from an offer and shows it again from the profile (en)', async ({ page }) => {
  await mockFeed(page, 'en');
  const blocks: { id: number; name: string }[] = [];
  const calls: string[] = [];
  await page.route('**/api/offers/blocked-publishers', route => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: blocks });
    }
    const block = { id: 8 + blocks.length, name: route.request().postDataJSON().name };
    blocks.push(block);
    calls.push(`block ${block.name}`);
    return route.fulfill({ json: block });
  });
  await page.route('**/api/offers/blocked-publishers/*', route => {
    const id = Number(new URL(route.request().url()).pathname.split('/').at(-1));
    calls.push(`unblock ${id}`);
    blocks.splice(blocks.findIndex(block => block.id === id), 1);
    return route.fulfill({ status: 204 });
  });
  await page.route('**/api/users/profile', route => route.fulfill({ json: {
    id: 1, firstName: 'Workspace', lastName: 'Test', email: 'workspace@example.test', targetDailyRate: 600
  } }));
  await page.route('**/api/users/security', route => route.fulfill({ json: { twoFactorEnabled: false } }));
  await page.route('**/api/users/notifications', route => route.fulfill({ json: {} }));

  await page.goto('/offers');
  const cards = page.locator('app-offer-card');
  await expect(cards).toHaveCount(2);
  await cards.first().getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Hide every offer from Exemple Conseil' }).click();
  await expect(page.getByText('Offers from Exemple Conseil are hidden from your feed. Bring them back from your profile.')).toBeVisible();
  await expect(cards).toHaveCount(1);
  await expect(cards.first()).toContainText('Data engineer Spark');

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Offer feed' }).click();
  await expect(page.locator('[data-block="Exemple Conseil"]')).toBeVisible();
  await page.getByLabel('Intermediary or client').fill('Acme');
  await page.getByRole('button', { name: 'Hide its offers' }).click();
  await expect(page.locator('[data-block="Acme"]')).toBeVisible();
  await page.getByRole('button', { name: 'Show offers from Exemple Conseil again' }).click();
  await expect(page.locator('[data-block="Exemple Conseil"]')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Choose the job boards of your feed' })).toHaveAttribute('href', '/offers/sites');
  expect(calls).toEqual(['block Exemple Conseil', 'block Acme', 'unblock 8']);
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('blocked-publishers-en.png'), fullPage: true });
});

test('moves through the feed and acts on offers with the keyboard (en)', async ({ page }) => {
  const api = await mockFeed(page, 'en');

  await page.goto('/offers');
  const items = page.locator('[data-offer-item]');
  await expect(items).toHaveCount(2);
  await items.first().focus();
  await page.keyboard.press('j');
  await expect(items.nth(1)).toBeFocused();
  await page.keyboard.press('s');
  await expect.poll(() => api.states).toContainEqual({ saved: true });
  await page.keyboard.press('k');
  await expect(items.first()).toBeFocused();
  await page.keyboard.press('d');
  await expect(items.first().locator('[data-description]')).toContainText('Refonte complète du SI bancaire.');
  await page.keyboard.press('h');
  await expect(items).toHaveCount(1);
  await expect(items.first()).toBeFocused();
  expect(api.states).toContainEqual({ hidden: true });

  await page.keyboard.press('Alt+Shift+H');
  await expect(page.getByRole('dialog').locator('[data-feed-shortcuts]')).toContainText('Add the offer to the pipeline');
});

test('keeps a private note on an offer and edits it from the keyboard (en)', async ({ page }) => {
  const api = await mockFeed(page, 'en');

  await page.goto('/offers');
  const cards = page.locator('app-offer-card');
  await expect(cards).toHaveCount(2);
  await cards.first().getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Add a note' }).click();
  const note = cards.first().getByLabel('Your note');
  await expect(note).toBeFocused();
  await note.fill('Called Julie: rate 650, starts in November');
  await cards.first().getByRole('button', { name: 'Save the note' }).click();
  await expect(cards.first().locator('[data-note]')).toContainText('Called Julie: rate 650, starts in November');
  expect(api.states).toContainEqual({ note: 'Called Julie: rate 650, starts in November' });
  await expect(cards.first().getByRole('button', { name: 'More actions' })).toBeFocused();

  await page.keyboard.press('n');
  await expect(note).toBeFocused();
  await expect(note).toHaveValue('Called Julie: rate 650, starts in November');
  await note.fill('Not for me: onsite every day');
  await page.keyboard.press('Escape');
  await expect(cards.first().locator('[data-note]')).toContainText('Called Julie');
  await page.keyboard.press('n');
  await note.fill('Not for me: onsite every day');
  await page.keyboard.press('Control+Enter');
  await expect(cards.first().locator('[data-note]')).toContainText('Not for me: onsite every day');
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('offer-note-en.png'), fullPage: true });
});

test('records an application from an offer and follows it in the applied view (en)', async ({ page }) => {
  await mockFeed(page, 'en');
  const applications: { method: string; body: unknown }[] = [];
  await page.route('**/api/offers/*/application', route => {
    const method = route.request().method();
    const body = method === 'POST' ? route.request().postDataJSON() : null;
    applications.push({ method, body });
    return route.fulfill({ json: { offerId: 1, saved: false, hidden: false, seen: true, importedProjectId: 40,
      ...(method === 'POST' ? { application: { appliedOn: body.appliedOn, via: body.via } } : {}) } });
  });

  await page.route(/\/api\/offers(\?.*)?$/, route => {
    if (new URL(route.request().url()).searchParams.get('view') !== 'APPLIED') {
      return route.fallback();
    }
    const content = [offer(1, { seen: true, importedProjectId: 40, application: { appliedOn: '2026-10-15', via: 'EMAIL' } })];
    return route.fulfill({ json: { content, page: 0, size: 20, totalElements: 1, totalPages: 1, distanceAvailable: false,
      matchAvailable: false } });
  });

  await page.goto('/offers');
  const cards = page.locator('app-offer-card');
  await expect(cards).toHaveCount(2);
  await cards.first().getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'I applied' }).click();
  const dialog = page.getByRole('dialog', { name: 'Record your application' });
  await expect(dialog).toContainText('The mission joins your pipeline at the Contact stage');
  await dialog.getByRole('radio', { name: 'Email' }).check();
  await dialog.getByLabel('Follow up after (days)').fill('5');
  await expect(dialog.locator('[data-follow-up-day]')).toContainText('in your Timeline');
  await settle(page);
  await page.screenshot({ path: test.info().outputPath('offer-apply-en.png') });
  await dialog.getByRole('button', { name: 'Record' }).click();

  await expect(page.getByText('Application to “Développeur Java 1” recorded; its follow-up is in your Timeline')).toBeVisible();
  await expect(cards.first().locator('[data-applied]')).toHaveText(/Applied today/);
  await expect(cards.first().getByRole('link', { name: 'In the pipeline' })).toHaveAttribute('href', '/projects/40');
  expect(applications[0].body).toEqual(expect.objectContaining({ via: 'EMAIL', followUpInDays: 5, language: 'EN' }));

  await page.locator('[data-view="APPLIED"]').click();
  await expect(page).toHaveURL(/view=APPLIED/);
  await cards.first().getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Undo the application' }).click();
  await expect(page.getByText('Application undone; the opportunity stays in your pipeline.')).toBeVisible();
  expect(applications.map(call => call.method)).toEqual(['POST', 'DELETE']);
});
