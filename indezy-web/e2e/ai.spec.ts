import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';
import { mockFeed } from './helpers/offer-feed';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const status = { available: true, reason: null, ownKey: false, model: 'claude-opus-5-5', spentUsd: 0.4, limitUsd: 2 };

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`fills an opportunity in from a pasted posting and drafts a message with AI (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    const asked: unknown[] = [];
    await page.route('**/api/ai/posting', route => {
      asked.push(route.request().postDataJSON());
      return route.fulfill({ json: { role: 'Développeur Java Senior', company: 'Conseil X', companyIsIntermediary: true, endClient: null,
        dailyRate: 650, techStack: ['Java', 'Kafka'], place: '92400 Courbevoie', workMode: 'HYBRID', remoteDaysPerMonth: 8,
        startDate: '2027-01-04', durationInMonths: 6, summary: null } });
    });
    await page.route('**/api/ai/message', route => {
      asked.push(route.request().postDataJSON());
      return route.fulfill({ json: { subject: 'Mission Java Senior', body: 'Bonjour,\n\nJe reviens vers vous.\n\nCamille' } });
    });

    await page.goto('/projects/new');
    await page.locator('[data-ai-posting]').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-ai-posting-text]').fill('Mission Java Senior chez Conseil X, 650 €/j, Courbevoie, 2 jours de télétravail');
    await settleDialog(page);
    const postingAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(postingAudit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-ai-posting-read]').click();
    await expect(dialog).toHaveCount(0);
    await expect(page.locator('input[formControlName="role"]')).toHaveValue('Développeur Java Senior');
    await expect(page.locator('input[formControlName="dailyRate"]')).toHaveValue('650');
    await expect(page.locator('[data-from-offer]')).toBeVisible();
    expect(asked[0]).toEqual({ text: 'Mission Java Senior chez Conseil X, 650 €/j, Courbevoie, 2 jours de télétravail' });

    await page.goto('/projects/42');
    await page.locator('[data-ai-message]').click();
    const drafting = page.getByRole('dialog');
    await drafting.locator('[data-ai-message-notes]').fill(en ? 'Available in January' : 'Dispo en janvier');
    await drafting.locator('[data-ai-message-write]').click();
    await expect(drafting.locator('[data-ai-message-subject]')).toHaveValue('Mission Java Senior');
    await settleDialog(page);
    const messageAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(messageAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(asked[1]).toEqual(asked42(en));
    await page.keyboard.press('Escape');
    await expect(drafting).toHaveCount(0);

    await page.route('**/api/ai/fit', route => route.fulfill({ json: { score: 78, verdict: en ? 'A good match.' : 'Une bonne mission.',
      pros: ['Java'], cons: [en ? 'Far' : 'Loin'], softCriteria: [], questions: [en ? 'Team size?' : "Taille de l'équipe ?"],
      rateAdvice: en ? 'Within the market.' : 'Dans le marché.', suggestedRate: 700 } }));
    await page.locator('[data-ai-fit-open]').click();
    const weighing = page.getByRole('dialog');
    await weighing.locator('[data-ai-fit-weigh]').click();
    await expect(weighing.locator('[data-ai-fit-score]')).toContainText('78');
    await settleDialog(page);
    const fitAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(fitAudit.violations.map(violation => violation.id)).toEqual([]);
    await page.keyboard.press('Escape');
    await expect(weighing).toHaveCount(0);

    let tailoredUrl = '';
    await page.route(/\/api\/ai\/dossier\/42/, route => {
      tailoredUrl = route.request().url();
      return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
    });
    await page.locator('[data-dossier="tailored"]').click();
    const download = page.waitForEvent('download');
    await page.locator('[data-dossier-choice="ANONYMOUS-EN"]').click();
    expect((await download).suggestedFilename()).toBe('skills-profile-anonymous-tailored.pdf');
    expect(tailoredUrl).toContain('/api/ai/dossier/42?layout=ANONYMOUS&language=EN');
    await expect(page.locator('[data-dossier="tailored"]')).toBeEnabled();

    const turns = [{ id: 1, fromMember: true, text: en ? 'How should I negotiate the rate for this mission?' : 'Comment négocier le TJM de cette mission ?',
      createdAt: '2026-11-22T10:00:00' }, { id: 2, fromMember: false, text: en ? 'Anchor at 720 €.' : 'Ancrez à 720 €.', createdAt: '2026-11-22T10:00:05' }];
    await page.route('**/api/ai/chat/42', route => route.fulfill({ json: route.request().method() === 'POST' ? turns : [] }));
    const chat = page.locator('[data-ai-chat]');
    await chat.locator('[data-ai-chat-open]').click();
    await expect(chat.locator('[data-ai-chat-question]')).toBeVisible();
    await chat.locator('[data-ai-chat-suggestion="negotiation"]').click();
    await expect(chat.locator('[data-ai-chat-question]')).not.toHaveValue('');
    await chat.locator('[data-ai-chat-ask]').click();
    await expect(chat.locator('[data-ai-chat-turn="ai"]')).toContainText(en ? 'Anchor at 720 €.' : 'Ancrez à 720 €.');
    const chatAudit = await new AxeBuilder({ page }).include('[data-ai-chat]').withTags(WCAG).analyze();
    expect(chatAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });

  test(`triages a feed offer on its page (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockFeed(page, language);
    await page.route(/\/api\/ai\/offers\/1\/verdict/, route => route.request().method() === 'POST' ?
      route.fulfill({ json: { offerId: 1, goodFit: false, verdict: en ? 'On site every day.' : 'Sur site tous les jours.',
        dealBreakers: [en ? '5 days on site' : '5 jours sur site'], createdAt: '2026-11-22T10:00:00' } }) :
      route.fulfill({ status: 204, body: '' }));
    await page.goto('/offers/1');
    const card = page.locator('[data-offer-verdict]');
    await card.locator('[data-offer-verdict-ask]').click();
    await expect(card.locator('[data-offer-deal-breakers]')).toContainText(en ? '5 days on site' : '5 jours sur site');
    const audit = await new AxeBuilder({ page }).include('[data-offer-verdict]').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
  });

  test(`shows the AI allowance and takes the member's own key (${language}, ${width}px)`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    let current: Record<string, unknown> = status;
    const keys: unknown[] = [];
    await page.route('**/api/ai/status', route => route.fulfill({ json: current }));
    await page.route('**/api/ai/key', route => {
      keys.push(route.request().postDataJSON());
      current = { ...status, ownKey: true, model: 'claude-haiku-5-5' };
      return route.fulfill({ json: current });
    });
    await page.goto('/profile');
    await page.getByRole('tab', { name: language === 'en' ? 'Preferences' : 'Préférences' }).click();
    const card = page.locator('[data-ai-settings]');
    await expect(card.locator('[data-ai-allowance]')).toContainText(language === 'en' ? '$0.40 of $2.00' : '0,40 $ sur 2,00 $');
    const audit = await new AxeBuilder({ page }).include('[data-ai-settings]').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await card.locator('[data-ai-key]').fill('sk-ant-camille');
    await card.locator('[data-ai-save-key]').click();
    await expect(card.locator('[data-ai-remove-key]')).toBeVisible();
    expect(keys).toEqual([{ apiKey: 'sk-ant-camille', model: 'claude-opus-5-5' }]);
  });
}

/** What drafting asks for opportunity 42, at the interview stage: a follow-up. */
function asked42(en: boolean): unknown {
  return { projectId: 42, kind: 'FOLLOW_UP', language: en ? 'EN' : 'FR', notes: en ? 'Available in January' : 'Dispo en janvier' };
}
