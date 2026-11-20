import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`notes who referred a mission and the bonus per day worked (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/projects/42', route => route.fulfill({ json: {
      id: 42, role: 'Lead Kafka', status: 'WON', clientName: 'Acme', dailyRate: 600, workMode: 'HYBRID', startDate: '2026-11-01',
      durationInMonths: 3, sourceName: 'Indezy @leo_m'
    } }));
    const saved: Record<string, unknown>[] = [];
    await page.route('**/api/projects/42/referral', route => {
      if (route.request().method() === 'GET') {
        return route.fulfill({ status: 204 });
      }
      const referral = route.request().postDataJSON();
      saved.push(referral);
      return route.fulfill({ json: { projectId: 42, role: 'Lead Kafka', clientName: 'Acme', referral, workedDays: 12, earned: 7200,
        bonus: 240 } });
    });

    await page.goto('/projects/42');
    const card = page.locator('app-referral-card');
    await card.locator('[data-referral-add]').click();
    await expect(card.locator('[data-referral-referrer]')).toHaveValue('@leo_m');
    await card.locator('[data-referral-kind]').click();
    await page.getByRole('option', { name: en ? 'Per day worked' : 'Par jour travaillé' }).click();
    await card.locator('[data-referral-amount]').fill('20');
    await card.locator('[data-referral-status]').click();
    await page.getByRole('option', { name: en ? 'Due' : 'À régler' }).click();
    const audit = await new AxeBuilder({ page }).include('app-referral-card').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await card.locator('[data-referral-save]').click();
    await expect(card.locator('[data-referral-who]')).toContainText(en ? 'Referred by @leo_m' : 'Recommandée par @leo_m');
    await expect(card.locator('[data-referral-bonus]')).toContainText('240');
    expect(saved).toEqual([{ referrer: '@leo_m', direction: 'I_PAY', bonusKind: 'PER_DAY', bonusAmount: 20, status: 'DUE', paidOn: null,
      note: null }]);
  });
}
