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
    await page.route('**/api/contacts/emails/availability-recipients', route => route.fulfill({ json: [
      { id: 7, firstName: 'Léa', name: 'Léa Martin', company: 'Conseil Exemple', email: 'lea@conseil.example', answers: 2,
        lastContactedOn: '2026-08-01', postings: 3, mission: 'Lead Java', silentCampaigns: 1, campaignOpportunities: 0 }] }));
    const emails: Record<string, unknown>[] = [];
    await page.route('**/api/contacts/7/emails', route => {
      emails.push(route.request().postDataJSON());
      return route.fulfill({ status: 201, json: { id: 30, channel: 'EMAIL', direction: 'OUTGOING' } });
    });
    const campaigns: { id: number; name: string; startedAt: string; sent: number; waiting: number; answered: number; opportunities: number;
      won: number }[] = [];
    await page.route('**/api/contacts/emails/campaigns', route => {
      if (route.request().method() === 'POST') {
        const campaign = { id: 50 + campaigns.length, name: route.request().postDataJSON().name, startedAt: '2026-11-23T08:00:00', sent: 1,
          waiting: 0, answered: 0, opportunities: 0, won: 0 };
        campaigns.push(campaign);
        return route.fulfill({ json: campaign });
      }
      return route.fulfill({ json: campaigns });
    });
    const plans: Record<string, unknown>[] = [];
    const waiting: Record<string, unknown>[] = [];
    await page.route('**/api/contacts/emails/scheduled', route => {
      if (route.request().method() === 'POST') {
        plans.push(route.request().postDataJSON());
        waiting.push({ id: 40, contactId: 7, name: 'Léa Martin', company: 'Conseil Exemple', subject: 'x', sendAt: '2027-01-25T08:00:00Z' });
        return route.fulfill({ json: waiting });
      }
      return route.fulfill({ json: waiting });
    });
    await page.route('**/api/contacts/emails/scheduled/40', route => {
      waiting.splice(0);
      return route.fulfill({ status: 204 });
    });
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

    // The ESN contacts worked with, written to once ticked, the dossier attached.
    await dialog.locator('[data-availability-emails] summary').click();
    await expect(dialog.locator('[data-availability-emails-body]')).toHaveValue(new RegExp(en ? 'Free on 1 February' : 'Dispo le 1er février'));
    const emailsAudit = await new AxeBuilder({ page }).include('[data-availability-emails]').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(emailsAudit.violations.map(violation => violation.id)).toEqual([]);
    await expect(dialog.locator('[data-availability-emails-send]')).toBeDisabled();
    await expect(dialog.locator('[data-availability-recipient="7"] [data-recipient-postings]'))
      .toHaveText(en ? 'posted 3 matching missions lately' : 'a publié 3 missions pour vous récemment');
    await expect(dialog.locator('[data-availability-recipient="7"] [data-recipient-silent]'))
      .toHaveText(en ? 'unanswered campaigns: 1' : 'campagnes sans réponse : 1');
    await dialog.locator('[data-availability-recipient="7"] input').check();
    // Her own message, written from the shared one, then a word added.
    await dialog.locator('[data-recipient-edit="7"]').click();
    const own = dialog.locator('[data-recipient-own="7"] textarea');
    await expect(own).toHaveValue(en ? /^Hello Léa,/ : /^Bonjour Léa,/);
    await own.fill((await own.inputValue()) + '\nP.S. Lead Java');
    await expect(dialog.locator('[data-recipient-edit="7"]')).toHaveText(en ? 'Edited' : 'Modifié');
    await dialog.locator('[data-availability-emails-send]').click();
    await expect.poll(() => emails.length).toBe(1);
    expect(String(emails[0]['body'])).toContain('P.S. Lead Java');
    expect(emails[0]).toEqual(expect.objectContaining({ campaignId: 50 }));
    await expect(dialog.locator('[data-campaign="50"]')).toContainText(en ? '1 sent · 0 answered' : '1 envoyés · 0 réponses');
    expect(emails[0]).toEqual(expect.objectContaining({ dossier: true, track: false }));
    expect(String(emails[0]['body'])).toMatch(en ? /^Hello Léa,/ : /^Bonjour Léa,/);

    // Planned over working days instead, then cancelled.
    await dialog.locator('[data-availability-recipient="7"] input').check();
    await dialog.locator('[data-availability-emails-days]').click();
    await page.getByRole('option', { name: en ? 'Over 3 working days' : 'Sur 3 jours ouvrés' }).click();
    await dialog.locator('[data-availability-emails-daily-max]').fill('5');
    await dialog.locator('[data-availability-emails-send]').click();
    await expect.poll(() => plans.length).toBe(1);
    expect(plans[0]).toEqual(expect.objectContaining({ days: 3, dailyMax: 5, dossier: true, campaignId: 51 }));
    expect(emails).toHaveLength(1);
    const planned = dialog.locator('[data-scheduled-email="40"]');
    await expect(planned).toContainText('Léa Martin · Conseil Exemple');
    await planned.locator('[data-scheduled-email-cancel]').click();
    await expect(planned).toHaveCount(0);
  });
}
