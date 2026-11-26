import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const SLUGS = ['getting-started', 'account', 'shortcuts', 'faq', 'offers', 'pipeline', 'clients-contacts', 'calendar', 'activity-reports',
  'invoices-quotes', 'document-templates', 'documents', 'dashboard', 'network', 'whats-new'];
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
    // Was this helpful?, answered once.
    const answers: unknown[] = [];
    await page.route('**/api/public/help/network/feedback', route => {
      answers.push(route.request().postDataJSON());
      return route.fulfill({ status: 204 });
    });
    await page.locator('[data-help-helpful-yes]').click();
    await expect.poll(() => answers).toEqual([{ helpful: true }]);
    await expect(page.locator('[data-help-helpful] [role=status]')).toHaveText(en ? 'Thanks for telling us.' : 'Merci de nous le dire.');

    await page.goto('/help');
    await expect(page.locator('[data-site-footer]')).toBeVisible();
    // The help page takes the same full width as every other workspace view.
    const widths = await page.evaluate(() => {
      const content = document.querySelector('.app-content')!.getBoundingClientRect();
      const style = getComputedStyle(document.querySelector('.app-content')!);
      const inner = content.width - parseFloat(style.paddingLeft) - parseFloat(style.paddingRight);
      const help = document.querySelector('.help-page')!.getBoundingClientRect().width;
      return { inner, help };
    });
    expect(Math.abs(widths.inner - widths.help)).toBeLessThanOrEqual(2);
    // Even with a short empty search result, the footer stays anchored at the bottom of the viewport.
    await page.locator('[data-help-search]').fill('zzzzzz-no-match');
    await expect(page.locator('[data-help-none]')).toBeVisible();
    const footerBottom = await page.locator('[data-site-footer]').evaluate(el => el.getBoundingClientRect().bottom);
    expect(footerBottom).toBeGreaterThanOrEqual(895);
    await page.locator('[data-help-search]').fill(en ? 'invoice' : 'facture');
    await expect(page.locator('[data-help-hit="invoices-quotes"]')).toBeVisible();
  });
}

test('reads the help and the legal texts without an account, linked from the presentation page', async ({ page }) => {
  await page.goto('/');
  // Visitors without an account write to the contact address.
  await expect(page.locator('[data-home-contact]')).toHaveAttribute('href', 'mailto:contact@swirlit.dev');
  await page.locator('[data-home-legal="privacy"]').click();
  await expect(page).toHaveURL(/\/legal\/privacy$/);
  await expect(page.locator('[data-legal="privacy"] [data-markdown] h2').first()).toBeVisible();
  const audit = await new AxeBuilder({ page }).include('.legal-page').withTags(AUDIT).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  await page.locator('[data-legal-link="notice"]').click();
  await expect(page.locator('[data-legal="notice"] [data-markdown]')).toContainText('OVH');
  await expect(page.locator('[data-legal="notice"] [data-markdown]')).toContainText('contact@swirlit.dev');
  await page.goto('/help/getting-started');
  await expect(page.locator('app-public-page-header')).toBeVisible();
  await expect(page.locator('[data-markdown] h2').first()).toBeVisible();
});

test('opens the help in the language of its address for a visitor', async ({ page }) => {
  await page.goto('/en/help/offers');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('[data-help-read="offers"] h1')).toHaveText('The offer feed');
  await page.goto('/aide/offers');
  await expect(page.locator('html')).toHaveAttribute('lang', 'fr');
  await expect(page.locator('[data-help-read="offers"] h1')).toHaveText('Le flux d’offres');
  await page.goto('/aide');
  await expect(page.locator('[data-help-article="invoices-quotes"]')).toBeVisible();
});
