import { expect, test } from '@playwright/test';
import { wcagViolations } from './helpers/axe';
import { mockWorkspaceSession } from './helpers/workspace';

const author = { handle: 'ines_b', firstName: 'Inès', lastName: 'Bernard', avatar: null, headline: null, stack: [], city: null,
  remote: false, availability: null, availabilityDate: null, languages: [], verified: false };
const idea = { id: 4, title: 'Export CSV des factures', description: 'Le besoin, un exemple.', status: 'PLANNED', statusNote: 'Au printemps',
  votes: 12, voted: false, own: false, author, createdAt: '2026-11-18T09:00:00', statusChangedAt: '2026-11-20T09:00:00', mergedInto: null };

for (const language of ['en', 'fr'] as const) {
  test(`meets WCAG 2.1 AA on an idea's page (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await page.route('**/api/users/capabilities', route => route.fulfill({ json: { admin: false, features: { SOCIAL_NETWORK: true } } }));
    await page.route('**/api/network/ideas?**', route => route.fulfill({ json: { ideas: [idea], page: 0, more: false, administrator: false } }));
    await page.route('**/api/network/ideas/4', route => route.fulfill({ json: idea }));

    await page.goto('/network/ideas/4');
    await expect(page.getByText('Export CSV des factures').first()).toBeVisible();
    await page.waitForLoadState('networkidle');
    expect(await wcagViolations(page), 'idea page').toEqual([]);

  });
}
