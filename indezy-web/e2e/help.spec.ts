import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const SLUGS = ['getting-started', 'account', 'shortcuts', 'faq', 'offers', 'pipeline', 'clients-contacts', 'calendar', 'activity-reports',
  'invoices-quotes', 'documents', 'dashboard', 'network', 'whats-new'];
const AUDIT = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

test('every help article and legal text exists in French and English, its screenshots too', async ({ request }) => {
  for (const language of ['fr', 'en']) {
    for (const slug of SLUGS) {
      const article = await request.get(`/assets/help/${language}/${slug}.md`);
      expect(article.ok(), `${language}/${slug}.md`).toBe(true);
      const text = await article.text();
      expect(text.length, `${language}/${slug}.md is written`).toBeGreaterThan(400);
      for (const image of text.matchAll(/!\[[^\]]*\]\(([^)]+)\)/g)) {
        expect((await request.get(image[1])).ok(), `${image[1]} in ${language}/${slug}.md`).toBe(true);
      }
      for (const link of text.matchAll(/\]\(\/help\/([a-z-]+)/g)) {
        expect(SLUGS, `link to /help/${link[1]} in ${language}/${slug}.md`).toContain(link[1]);
      }
    }
    for (const document of ['notice', 'terms', 'privacy']) {
      expect((await request.get(`/assets/legal/${language}/${document}.md`)).ok(), `legal ${language}/${document}`).toBe(true);
    }
  }
});

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`opens the help from the menu, reads an article and searches (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.goto('/dashboard');
    if (width < 900) {
      await page.getByRole('button', { name: en ? 'Toggle navigation menu' : 'Afficher ou masquer le menu' }).click();
    }
    await page.getByRole('link', { name: en ? 'Help' : 'Aide', exact: true }).click();
    await expect(page).toHaveURL(/\/help$/);
    await expect(page.locator('[data-help-article]')).toHaveCount(SLUGS.length);
    const audit = await new AxeBuilder({ page }).include('.help-page').withTags(AUDIT).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);

    await page.locator('[data-help-article="network"]').click();
    await expect(page).toHaveURL(/\/help\/network$/);
    await expect(page.locator('[data-markdown] h2').first()).toBeVisible();
    await expect(page.locator('[data-markdown] img').first()).toBeVisible();
    const article = await new AxeBuilder({ page }).include('.article-page').withTags(AUDIT).analyze();
    expect(article.violations.map(violation => violation.id)).toEqual([]);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);

    await page.goto('/help');
    await page.locator('[data-help-search]').fill(en ? 'invoice' : 'facture');
    await expect(page.locator('[data-help-hit="invoices-quotes"]')).toBeVisible();
  });
}

test('reads the help and the legal texts without an account, linked from the presentation page', async ({ page }) => {
  await page.goto('/');
  await page.locator('[data-home-legal="privacy"]').click();
  await expect(page).toHaveURL(/\/legal\/privacy$/);
  await expect(page.locator('[data-legal="privacy"] [data-markdown] h2').first()).toBeVisible();
  const audit = await new AxeBuilder({ page }).include('.legal-page').withTags(AUDIT).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await page.locator('[data-legal-link="notice"]').click();
  await expect(page.locator('[data-legal="notice"] [data-markdown]')).toContainText('OVH');
  await page.goto('/help/getting-started');
  await expect(page.locator('app-public-page-header')).toBeVisible();
  await expect(page.locator('[data-markdown] h2').first()).toBeVisible();
});
