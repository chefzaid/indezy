import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`shows this week's goal against the bench on the dashboard (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route(/\/api\/projects\/stats\/dashboard\/1/, route => route.fulfill({ json: { ...emptyDashboardStats, totalProjects: 3,
      followUpsDue: [{ contactId: 2, name: 'Alice Martin', clientName: 'Acme', channel: 'EMAIL', sentOn: '2026-11-17', daysWaiting: 5,
        usualAnswerDays: 3 }],
      benchPlan: { projectId: 42, role: 'Java developer', endDate: '2027-01-04', weeksLeft: 7, activeOpportunities: 2,
        advancedOpportunities: 1, addedThisWeek: 1, daysSinceLastAdded: 9, weeklyGoal: 3 } } }));

    await page.goto('/dashboard');
    const card = page.locator('[data-bench-plan]');
    await expect(card).toContainText(en ? 'Java developer ends in 7 weeks' : 'Java developer se termine dans 7 semaines');
    await expect(card).toContainText(en ? 'Add 3 opportunities this week.' : 'Ajoutez 3 opportunités cette semaine.');
    await expect(card.locator('.count')).toHaveText('1/3');
    await expect(page.locator('[data-usual-answer]')).toContainText(en ? 'usually answers in 3 days' : 'répond en général en 3 jours');
    const audit = await new AxeBuilder({ page }).include('[data-bench-plan]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    // The availability announced with AI: both posts drafted, the network one kept as a draft.
    await page.route('**/api/ai/availability**', route => route.fulfill({ json: { linkedin: en ? 'Available from February.' : 'Disponible en février.',
      network: en ? 'Free on 1 February.' : 'Dispo le 1er février.', availableFrom: '2027-02-01' } }));
    const drafts: unknown[] = [];
    await page.route('**/api/network/drafts', route => {
      drafts.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: { id: 3, text: 'x' } });
    });
    await card.locator('[data-bench-announce]').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-ai-availability-write]').click();
    await expect(dialog.locator('[data-ai-availability-linkedin]')).toHaveValue(en ? 'Available from February.' : 'Disponible en février.');
    await expect(dialog.locator('[data-ai-availability-from]')).toContainText('2027');
    await settleDialog(page);
    const dialogAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(dialogAudit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-ai-availability-draft]').click();
    await expect.poll(() => drafts.length).toBe(1);
    expect(drafts[0]).toEqual({ text: en ? 'Free on 1 February.' : 'Dispo le 1er février.', scheduledAt: null });
  });
}
