import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`sets the feed's filters from a search described in words (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/ai/offer-brief', route => route.fulfill({ json: { skills: ['Kafka'], rateMin: 650, minRemoteDays: 2,
      workModes: [], regions: ['11'], startBefore: null, durationMin: null, q: null, avoid: [en ? 'banking' : 'banque'] } }));

    await page.goto('/offers');
    await page.locator('[data-offer-brief]').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-offer-brief-text]').fill(en ? 'Kafka, 650 € at least, 2 remote days, Paris area, no banking' :
      'Kafka, 650 € minimum, 2 jours de télétravail, Île-de-France, pas de banque');
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-offer-brief-read]').click();
    await expect(dialog).toHaveCount(0);
    await expect(page).toHaveURL(/skills=Kafka/);
    await expect(page).toHaveURL(/rateMin=650/);
    await expect(page).toHaveURL(/regions=11/);
    await expect(page).toHaveURL(en ? /exclude=banking/ : /exclude=banque/);
    await expect(page.getByText(en ? /Left out the offers mentioning: banking/ : /Offres écartées qui mentionnent : banque/)).toBeVisible();
  });
}
