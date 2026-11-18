import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

test('asks an external person by email for a testimonial, then shows it once written', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await mockWorkspaceData(page, 'en');
  let testimonials: Record<string, unknown>[] = [];
  await page.route('**/api/users/public-profile', route => route.fulfill({ json: { enabled: true, slug: 'workspace-test' } }));
  await page.route('**/api/testimonials', route => {
    if (route.request().method() === 'POST') {
      expect(route.request().postDataJSON()).toEqual({ recipientFirstName: 'Paul', recipientLastName: 'Durand',
        recipientEmail: 'paul@acme.example' });
      const asked = { id: 4, recipientName: 'Paul Durand', recipientEmail: 'paul@acme.example', status: 'REQUESTED',
        expiresAt: '2026-12-14T10:00:00Z' };
      testimonials = [asked];
      return route.fulfill({ status: 201, json: { token: 'paul-token', testimonial: asked, emailed: true } });
    }
    return route.fulfill({ json: testimonials });
  });
  await page.route('**/api/testimonials/9/status', route => route.fulfill({ json: { ...testimonials[0], status: 'APPROVED' } }));

  await page.goto('/profile');
  await page.getByRole('tab', { name: 'Public profile' }).click();
  const section = page.locator('[data-testimonials]');
  // The choice reads in full, and an external person is asked by their names and email.
  await expect(section.locator('[data-testimonial-contact]')).toContainText('External person (outside your contacts)');
  await section.locator('[data-testimonial-first-name]').fill('Paul');
  await section.locator('[data-testimonial-last-name]').fill('Durand');
  await section.locator('[data-testimonial-email]').fill('paul@acme');
  await section.locator('[data-ask-testimonial]').click();
  await expect(section.getByText('Enter a valid email address.')).toBeVisible();
  await section.locator('[data-testimonial-email]').fill('paul@acme.example');
  await section.locator('form').screenshot({ path: test.info().outputPath('testimonial-ask.png') });
  await section.locator('[data-ask-testimonial]').click();
  await expect(section.locator('[data-testimonial-emailed]')).toContainText('An email with their personal link was sent to paul@acme.example.');
  await expect(section.locator('[data-testimonial-link] .url')).toHaveText(/\/testimonial#paul-token$/);
  await expect(section.locator('[data-testimonial="4"] [data-testimonial-status]')).toHaveText('Asked');

  testimonials = [{ id: 9, recipientName: 'Léa Martin', status: 'RECEIVED', expiresAt: '2026-12-14T10:00:00Z', authorName: 'Léa Martin',
    authorTitle: 'CTO, Acme', text: 'Un consultant rigoureux, qui a mené la refonte en temps et en heure.', receivedAt: '2026-11-14T10:00:00Z' }];
  await page.reload();
  await page.getByRole('tab', { name: 'Public profile' }).click();
  await section.locator('[data-testimonial="9"] [data-approve-testimonial]').click();
  await expect(section.locator('[data-testimonial="9"] [data-testimonial-status]')).toHaveText('Shown');
  const audit = await new AxeBuilder({ page }).include('[data-testimonials]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
});

for (const { language, width } of [{ language: 'en', width: 1280 }, { language: 'fr', width: 390 }]) {
  test(`writes a testimonial through its link, without an account (${language})`, async ({ page, context }) => {
    await page.setViewportSize({ width, height: 900 });
    await context.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
    let answered = false;
    const sent: unknown[] = [];
    await page.route('**/api/public/testimonials', route => {
      expect(route.request().headers()['x-testimonial-token']).toBe('lea-token');
      if (route.request().method() === 'POST') {
        sent.push(route.request().postDataJSON());
        answered = true;
      }
      return route.fulfill({ json: { freelancerName: 'Alice Martin', recipientName: 'Léa Martin', answered } });
    });

    await page.goto('/testimonial#lea-token');
    await expect(page.locator('[data-author-name]')).toHaveValue('Léa Martin');
    await page.locator('[data-author-title]').fill('CTO, Acme');
    await page.locator('[data-testimonial-text]').fill('Un consultant rigoureux, qui a mené la refonte en temps et en heure.');
    await page.locator('[data-send-testimonial]').click();
    expect(sent).toEqual([]);
    await page.locator('[data-testimonial-consent] input').check();
    const audit = await new AxeBuilder({ page }).include('.testimonial-page').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('[data-send-testimonial]').click();
    await expect(page.locator('[data-testimonial-thanks]')).toBeVisible();
    // Creating an account is offered, never needed.
    await expect(page.locator('[data-testimonial-join-link]')).toHaveAttribute('href', '/register');
    expect(sent).toEqual([{ authorName: 'Léa Martin', authorTitle: 'CTO, Acme',
      text: 'Un consultant rigoureux, qui a mené la refonte en temps et en heure.', consent: true }]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}

test('shows the approved testimonials on the public profile', async ({ page }) => {
  await page.route('**/api/public/profiles/alice', route => route.fulfill({ json: { firstName: 'Alice', lastName: 'Martin', skills: [],
    languages: [], availableNow: true, testimonials: [{ authorName: 'Léa Martin', authorTitle: 'CTO, Acme', text: 'Rigoureuse et fiable.' }] } }));
  await page.goto('/p/alice');
  await expect(page.locator('[data-public-testimonials]')).toContainText('Rigoureuse et fiable.');
  await expect(page.locator('[data-public-testimonials] figcaption')).toHaveText('Léa Martin, CTO, Acme');
});
