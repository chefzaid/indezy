import { expect, test } from '@playwright/test';
import { wcagViolations } from './helpers/axe';
import { mockFeed } from './helpers/offer-feed';

const trainings = [
  { id: 'cpf-1', title: 'Kubernetes : administration', provider: 'Orsys', certification: 'CKA', hours: 35, price: 2450, remote: true, regions: [] },
  { id: 'cpf-2', title: 'Kubernetes pour les développeurs', provider: 'Ib Cegos', certification: null, hours: 21, price: null, remote: false,
    regions: ['Île-de-France'] }];

for (const language of ['en', 'fr'] as const) {
  const en = language === 'en';
  test(`finds a missing skill's CPF trainings and plans one (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    await mockFeed(page, language);
    let catalogueUp = false;
    const asked: string[] = [];
    await page.route('**/api/offers/skills-gap/trainings?**', route => {
      asked.push(new URL(route.request().url()).searchParams.get('skill') ?? '');
      return catalogueUp ? route.fulfill({ json: trainings }) : route.fulfill({ status: 503, json: {} });
    });
    const planned: unknown[] = [];
    await page.route('**/api/trainings', route => {
      planned.push(route.request().postDataJSON());
      return route.fulfill({ json: { id: 7, ...route.request().postDataJSON() } });
    });

    await page.goto('/offers');
    await page.getByRole('button', { name: en ? 'Market' : 'Marché' }).click();
    const gap = page.locator('[data-skills-gap]');
    const cpf = gap.locator('[data-skills-gap-cpf="Kubernetes"]');
    await expect(cpf).toHaveAttribute('aria-expanded', 'false');

    // The catalogue down: said, and read again on the next try.
    await cpf.click();
    await expect(gap.locator('[data-skills-gap-cpf-error]')).toContainText(en ? 'could not be read' : "n'a pas pu être lu");
    await cpf.click();
    catalogueUp = true;
    await cpf.click();
    await expect(cpf).toHaveAttribute('aria-expanded', 'true');
    const found = gap.locator('[data-skills-gap-trainings="Kubernetes"] [data-catalog-training]');
    await expect(found).toHaveCount(2);
    await expect(found.first()).toContainText('Orsys');
    await expect(found.first()).toContainText('2450 €');
    await expect(found.last()).toContainText('Île-de-France');
    expect(asked).toEqual(['Kubernetes', 'Kubernetes']);
    await page.waitForLoadState('networkidle');
    expect(await wcagViolations(page), 'CPF trainings').toEqual([]);

    await gap.locator('[data-catalog-plan="cpf-1"]').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.locator('[data-training-title]')).toHaveValue('Kubernetes : administration');
    await expect(dialog.locator('[data-training-certification]')).toHaveValue('CKA');
    await dialog.locator('[data-save-training]').click();
    await expect(page.getByText(en ? 'Training added to your trainings' : 'Formation ajoutée à vos formations')).toBeVisible();
    expect(planned).toEqual([expect.objectContaining({ title: 'Kubernetes : administration', provider: 'Orsys', certification: 'CKA' })]);
  });
}
