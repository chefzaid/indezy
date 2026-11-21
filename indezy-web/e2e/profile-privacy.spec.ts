import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`gathers every opt-in and opt-out in Profile → Privacy (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false,
      features: { SOCIAL_NETWORK: true, OFFER_FEED: true, PUBLIC_PROFILES: true, DATA_IMPORT: true, YEARLY_REVIEW: true } } }));
    const fields = { photo: true, headline: true, stack: true, location: true, availability: true, languages: true, remote: false };
    const membership = { joined: true, handle: 'camille', fields, availability: { mode: 'AUTO', date: null }, card: null, privateCard: false,
      esnSuggestions: false, contactSuggestions: true };
    await page.route('**/api/network/membership', route => route.fulfill({ json: membership }));
    const asked: string[] = [];
    await page.route(/\/api\/network\/membership\/contact-suggestions/, route => {
      asked.push(new URL(route.request().url()).searchParams.get('on') ?? '');
      return route.fulfill({ json: { ...membership, contactSuggestions: false } });
    });
    await page.route('**/api/network/messages/settings', route => route.fulfill({ json: { readReceipts: true, typing: true, presence: true,
      requests: true } }));
    await page.route('**/api/network/page-views', route => route.fulfill({ json: { viewers: 0, viewsThisWeek: 0, named: [], anonymous: 0,
      privateViewing: false } }));
    // What the network tab reads beside it, as the app's own endpoints answer.
    await page.route('**/api/network/restrictions', route => route.fulfill({ json: { muted: [], blocked: [] } }));
    await page.route('**/api/network/notifications/settings', route => route.fulfill({ json: { off: [], emailed: [] } }));
    await page.route('**/api/network/membership/about', route => route.fulfill({ json: { about: '', showMissions: false } }));
    await page.route('**/api/network/follow-requests', route => route.fulfill({ json: [] }));
    await page.route('**/api/users/notifications', route => route.fulfill({ json: { weeklyReports: true, weeklyDigestSections: [],
      bookingEmails: true, newOffersBadge: true, messageEmails: true, emailsAvailable: true } }));

    await page.goto('/profile?tab=privacy');
    const privacy = page.locator('[data-privacy]');
    await expect(privacy.locator('[data-privacy-switch="contactSuggestions"]')).toContainText(en ? 'Suggestions by my contacts' :
      'Suggestions par mes contacts');
    const toggle = privacy.locator('[data-privacy-switch="contactSuggestions"] button[role="switch"]');
    await expect(toggle).toHaveAttribute('aria-checked', 'true');
    const audit = await new AxeBuilder({ page }).include('[data-privacy]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-checked', 'false');
    expect(asked).toEqual(['false']);
    await privacy.locator('[data-privacy-elsewhere="contactRetention"] button').click();
    await expect(page.getByRole('tab', { name: en ? 'Preferences' : 'Préférences' })).toHaveAttribute('aria-selected', 'true');
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(0);
  });
}
