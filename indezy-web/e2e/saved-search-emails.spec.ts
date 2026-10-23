import { expect, test } from '@playwright/test';
import { mockFeed, savedSearch, searchEmails } from './helpers/offer-feed';

test('sets a saved search to email its new offers daily (en)', async ({ page }) => {
  test.setTimeout(60_000);
  await mockFeed(page, 'en');
  const updates: unknown[] = [];
  await page.route('**/api/offers/searches/5/emails', route => {
    updates.push(route.request().postDataJSON());
    return route.fulfill({ json: { ...savedSearch, ...searchEmails({ frequency: 'DAILY', hour: 7, language: 'EN' }) } });
  });

  await page.goto('/offers');
  const bell = page.getByRole('button', { name: 'Emails for “Java remote”' });
  await expect(bell).toHaveAttribute('data-search-emails', 'OFF');
  await bell.click();

  const dialog = page.getByRole('dialog', { name: 'Email me the new offers of “Java remote”' });
  await dialog.getByRole('radio', { name: 'Once a day' }).check();
  await dialog.getByRole('combobox', { name: 'At' }).click();
  await page.getByRole('option', { name: '07:00' }).click();
  await expect(dialog.getByText('Paris time')).toBeVisible();
  await dialog.getByRole('button', { name: 'Save' }).click();

  await expect(page.getByText('The new offers of “Java remote” will be emailed daily')).toBeVisible();
  expect(updates).toEqual([{ frequency: 'DAILY', hour: 7, language: 'EN' }]);
  await expect(bell).toHaveAttribute('data-search-emails', 'DAILY');
});

test('stops a saved search\'s emails from their link without an account (fr)', async ({ page }) => {
  const tokens: (string | null)[] = [];
  await page.addInitScript(() => localStorage.setItem('indezy-lang', 'fr'));
  await page.route('**/api/public/saved-search-emails', route => {
    tokens.push(route.request().headers()['x-email-token']);
    return route.fulfill({ json: { searchName: 'Java remote', frequency: 'DAILY', language: 'FR' } });
  });
  await page.route('**/api/public/saved-search-emails/unsubscribe', route => {
    tokens.push(route.request().headers()['x-email-token']);
    return route.fulfill({ json: { searchName: 'Java remote', frequency: 'OFF', language: 'FR' } });
  });

  await page.goto('/emails/unsubscribe#tok3n');

  await expect(page.getByText('Vous recevez par email les nouvelles offres de la recherche « Java remote ».')).toBeVisible();
  await page.getByRole('button', { name: 'Arrêter ces emails' }).click();
  await expect(page.getByRole('heading', { name: 'Emails arrêtés' })).toBeVisible();
  await expect(page).toHaveURL(/\/emails\/unsubscribe#tok3n$/);
  // The token went in a header each time, never in an address.
  expect(tokens).toEqual(['tok3n', 'tok3n']);
});

test('says an unsubscribe link without its token is no longer valid (fr)', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('indezy-lang', 'fr'));

  await page.goto('/emails/unsubscribe');

  await expect(page.getByRole('heading', { name: 'Ce lien n\'est plus valide' })).toBeVisible();
});
