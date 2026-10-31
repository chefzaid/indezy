import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const { language, width, theme } of [{ language: 'en', width: 1280, theme: 'light' }, { language: 'fr', width: 390, theme: 'dark' }]) {
  test(`ticks a won mission's contract checklist with notes (${language})`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme, language, inactivityDays: 14 } }));
    let mission: Record<string, unknown> = { id: 42, role: 'Java developer', status: 'WON', clientId: 3, clientName: 'Acme',
      dailyRate: 650, workMode: 'HYBRID', freelanceId: 1, contractChecks: [{ point: 'NOTICE_PERIOD', checked: true, note: '15 jours' }] };
    const saves: unknown[] = [];
    await page.route('**/api/projects/42', route => route.fulfill({ json: mission }));
    await page.route('**/api/projects/42/contract-checks', route => {
      const { checks } = route.request().postDataJSON();
      saves.push(checks);
      mission = { ...mission, contractChecks: checks };
      return route.fulfill({ json: mission });
    });

    await page.goto('/projects/42');
    const card = page.locator('[data-contract-checklist]');
    await expect(card.locator('[data-contract-count]')).toHaveText('1/7');
    await card.locator('[data-contract-point="PAYMENT_TERMS"]').getByRole('checkbox').check();
    await expect(card.locator('[data-contract-count]')).toHaveText('2/7');
    const note = card.locator('[data-contract-point="PAYMENT_TERMS"] input[matInput]');
    await note.fill('45 jours fin de mois');
    await note.blur();
    await expect.poll(() => saves.length).toBe(2);
    expect(saves[1]).toEqual([{ point: 'PAYMENT_TERMS', checked: true, note: '45 jours fin de mois' },
      { point: 'NOTICE_PERIOD', checked: true, note: '15 jours' }]);
    const audit = await new AxeBuilder({ page }).include('[data-contract-checklist]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
