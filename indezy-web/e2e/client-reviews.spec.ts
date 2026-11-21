import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const published = { id: 5, kind: 'CLIENT', paymentRating: 2, processRating: 4, rateRating: 3, text: 'Paid at 60 days, but a quick process.',
  updatedAt: '2026-11-20T10:00:00', mine: false };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`reads the members' reviews of a client and writes one (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    let company: Record<string, unknown> = { companyName: 'Acme', kind: 'CLIENT', count: 1, paymentAverage: 2, processAverage: 4, rateAverage: 3,
      reviews: [published], mine: null, canReview: true };
    const written: unknown[] = [];
    await page.route('**/api/network/reviews/client/3', route => {
      if (route.request().method() === 'PUT') {
        written.push(route.request().postDataJSON());
        company = { ...company, mine: { id: 6, status: 'HELD', paymentRating: 1, processRating: 5, rateRating: 4, text: 'x',
          heldReasons: ['PHONE'], updatedAt: '2026-11-21T10:00:00' } };
      }
      return route.fulfill({ json: company });
    });

    await page.goto('/clients/3');
    const card = page.locator('[data-client-reviews]');
    await expect(card.locator('[data-review="5"]')).toContainText(en ? 'A member' : 'Un membre');
    await expect(card.locator('[data-reviews-averages]')).toContainText(en ? '2.0 / 5' : '2,0 / 5');

    await card.locator('[data-write-review]').click();
    for (const [rating, value] of [['paymentRating', '1'], ['processRating', '5'], ['rateRating', '4']]) {
      await card.locator(`[data-rating="${rating}"]`).getByRole('radio', { name: value, exact: true }).click();
    }
    await card.locator('[data-review-text]').fill(en ? 'Paid very late, call them at 06 12 34 56 78.' : 'Payé très en retard, appelez le 06 12 34 56 78.');
    const audit = await new AxeBuilder({ page }).include('[data-client-reviews]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await card.locator('[data-save-review]').click();
    await expect(card.locator('[data-my-review-status="HELD"]')).toBeVisible();
    expect(written).toEqual([{ paymentRating: 1, processRating: 5, rateRating: 4,
      text: en ? 'Paid very late, call them at 06 12 34 56 78.' : 'Payé très en retard, appelez le 06 12 34 56 78.' }]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
