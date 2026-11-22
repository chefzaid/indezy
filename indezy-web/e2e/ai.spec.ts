import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';
import { mockFeed } from './helpers/offer-feed';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const status = { available: true, reason: null, ownKey: false, model: null, spentUsd: 0.4, limitUsd: 2,
  models: { POSTING: 'claude-haiku-5-5', TRIAGE: 'claude-haiku-5-5', MESSAGE: 'claude-sonnet-5-5', DOSSIER: 'claude-sonnet-5-5',
    FIT: 'claude-opus-5-5', CHAT: 'claude-opus-5-5' } };

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
    await page.route('**/api/ai/posting/pdf', route => route.fulfill({ json: { text: 'Mission Kafka depuis un PDF', cut: true } }));
    await dialog.locator('[data-ai-posting-pdf-input]').setInputFiles({ name: 'annonce.pdf', mimeType: 'application/pdf',
      buffer: Buffer.from('%PDF-1.7') });
    await expect(dialog.locator('[data-ai-posting-text]')).toHaveValue('Mission Kafka depuis un PDF');
    await expect(dialog.locator('.cut')).toBeVisible();
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

    // A rate suggested from what the form now says, and put in the daily rate field.
    await page.route('**/api/ai/rate', route => {
      asked.push(route.request().postDataJSON());
      return route.fulfill({ json: { rate: 700, low: 650, high: 750, reasons: [en ? 'Senior Java is in demand.' : 'Le Java senior est demandé.'],
        marketMedian: 640 } });
    });
    const rate = page.locator('[data-ai-rate]');
    await rate.locator('[data-ai-rate-ask]').click();
    await expect(rate.locator('[data-ai-rate-suggestion]')).toContainText(en ? 'Senior Java is in demand.' : 'Le Java senior est demandé.');
    const rateAudit = await new AxeBuilder({ page }).include('[data-ai-rate]').withTags(WCAG).analyze();
    expect(rateAudit.violations.map(violation => violation.id)).toEqual([]);
    await rate.locator('[data-ai-rate-use]').click();
    await expect(page.locator('input[formControlName="dailyRate"]')).toHaveValue('700');
    expect(asked[1]).toEqual(expect.objectContaining({ role: 'Développeur Java Senior', techStack: 'Java, Kafka', workMode: 'HYBRID' }));
    asked.splice(1, 1);

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

    const notes: unknown[] = [];
    await page.route(/\/api\/ai\/interview\/42/, route => route.fulfill({ json: { technical: [{ question: en ? 'Kafka exactly-once?' : 'Kafka exactly-once ?',
      hint: en ? 'Idempotence.' : 'Idempotence.' }], behavioural: [], pitch: en ? 'Hello, I am Camille.' : 'Bonjour, je suis Camille.', toAsk: [] } }));
    await page.route(/\/api\/projects\/42\/notes$/, route => {
      if (route.request().method() === 'POST') {
        notes.push(route.request().postDataJSON());
        return route.fulfill({ status: 201, json: { id: 77, projectId: 42, content: 'x', createdAt: '2026-11-22T10:00:00' } });
      }
      return route.fulfill({ json: [] });
    });
    await page.locator('[data-ai-interview-open]').click();
    const coaching = page.getByRole('dialog');
    await expect(coaching.locator('[data-ai-interview-pitch]')).toContainText(en ? 'Hello, I am Camille.' : 'Bonjour, je suis Camille.');
    await settleDialog(page);
    const interviewAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(interviewAudit.violations.map(violation => violation.id)).toEqual([]);
    await coaching.locator('[data-ai-interview-note]').click();
    await expect.poll(() => notes.length).toBe(1);
    await page.keyboard.press('Escape');
    await expect(coaching).toHaveCount(0);

    await page.route(/\/api\/ai\/negotiation\/42/, route => route.fulfill({ json: { counterOffer: 680, walkAway: 620,
      strategy: en ? 'Anchor high.' : 'Ancrez haut.', arguments: [en ? 'Kafka is rare.' : 'Kafka est rare.'], marginEuros: 150, marginPercent: 20,
      marketMedian: 640 } }));
    await page.locator('[data-ai-negotiation-open]').click();
    const negotiating = page.getByRole('dialog');
    await expect(negotiating.locator('[data-ai-negotiation-counter]')).toContainText('680');
    await expect(negotiating).toContainText(en ? 'Kafka is rare.' : 'Kafka est rare.');
    await settleDialog(page);
    const negotiationAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(negotiationAudit.violations.map(violation => violation.id)).toEqual([]);
    await page.keyboard.press('Escape');
    await expect(negotiating).toHaveCount(0);

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

    // The application prepared as a LinkedIn message, with the screening answers and a rate.
    let asked = '';
    await page.route(/\/api\/ai\/offers\/1\/pack/, route => {
      asked = route.request().url();
      return route.fulfill({ json: { subject: '', message: en ? 'Hello, Kotlin lead available in January.' : 'Bonjour, lead Kotlin dispo en janvier.',
        screening: [{ question: en ? 'Availability' : 'Disponibilité', answer: en ? 'From January.' : 'Dès janvier.' }], rate: 620,
        rateReasons: [en ? 'Kotlin is rare.' : 'Kotlin est rare.'] } });
    });
    const pack = page.locator('[data-offer-pack]');
    await pack.getByRole('radio', { name: en ? 'LinkedIn message' : 'Message LinkedIn' }).click();
    await pack.locator('[data-offer-pack-write]').click();
    await expect(pack.locator('[data-offer-pack-message]')).toHaveValue(en ? 'Hello, Kotlin lead available in January.' :
      'Bonjour, lead Kotlin dispo en janvier.');
    expect(asked).toContain('channel=LINKEDIN');
    await expect(pack).toContainText(en ? 'From January.' : 'Dès janvier.');
    const packAudit = await new AxeBuilder({ page }).include('[data-offer-pack]').withTags(WCAG).analyze();
    expect(packAudit.violations.map(violation => violation.id)).toEqual([]);
  });

  test(`fills the profile in from a CV once reviewed (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/ai/profile-import', route => route.fulfill({ json: { firstName: 'Camille', lastName: 'Test', phone: null,
      city: 'Lyon', position: 'Lead Java', company: null, bio: null, website: null, linkedin: null, github: null,
      languages: ['Anglais'], skills: [{ name: 'java', years: 9 }, { name: 'Kafka', years: 4 }] } }));
    const saved: Record<string, unknown>[] = [];
    await page.route(/\/api\/users\/profile$/, route => {
      if (route.request().method() !== 'PUT') {
        return route.fallback();
      }
      saved.push(route.request().postDataJSON());
      return route.fulfill({ json: { id: 1, email: 'workspace@example.test', ...route.request().postDataJSON() } });
    });
    await page.goto('/profile');
    await page.locator('[data-cv-import]').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-cv-import-file]').setInputFiles({ name: 'cv.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') });
    // The first name differs from the profile's, so it is offered unticked; the empty city and position are ticked.
    await expect(dialog.locator('[data-cv-import-row="firstName"] input')).not.toBeChecked();
    await expect(dialog.locator('[data-cv-import-row="city"] input')).toBeChecked();
    await expect(dialog).toContainText(en ? 'Now: Workspace' : 'Actuellement : Workspace');
    await expect(dialog.locator('[data-cv-import-skills]')).toContainText('Kafka');
    await expect(dialog.locator('[data-cv-import-skills]')).not.toContainText('java');
    await expect(dialog.locator('[data-cv-import-row="lastName"]')).toHaveCount(0);
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-cv-import-row="position"] input').uncheck();
    await dialog.locator('[data-cv-import-apply]').click();
    await expect(dialog).toBeHidden();
    // Nothing is saved until the member saves the profile.
    expect(saved).toEqual([]);
    await page.locator('app-profile-personal-info button[type="submit"]').click();
    await expect.poll(() => saved.length).toBe(1);
    expect(saved[0]).toEqual(expect.objectContaining({ firstName: 'Workspace', city: 'Lyon', skills: ['Java', 'Kafka'],
      skillDetails: { Kafka: { years: 4 } }, languages: ['Anglais'] }));
    expect(saved[0]['position']).not.toBe('Lead Java');
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
      current = { ...status, ownKey: true };
      return route.fulfill({ json: current });
    });
    await page.goto('/profile');
    await page.getByRole('tab', { name: language === 'en' ? 'Preferences' : 'Préférences' }).click();
    const card = page.locator('[data-ai-settings]');
    await expect(card.locator('[data-ai-allowance]')).toContainText(language === 'en' ? '$0.40 of $2.00' : '0,40 $ sur 2,00 $');
    await card.locator('[data-ai-models] summary').click();
    await expect(card.locator('[data-ai-models]')).toContainText(language === 'en' ? 'Reading a posting' : "Lecture d'une annonce");
    await expect(card.locator('[data-ai-models]')).toContainText('Claude Haiku 5.5');
    const audit = await new AxeBuilder({ page }).include('[data-ai-settings]').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await card.locator('[data-ai-key]').fill('sk-ant-camille');
    await card.locator('[data-ai-save-key]').click();
    await expect(card.locator('[data-ai-remove-key]')).toBeVisible();
    expect(keys).toEqual([{ apiKey: 'sk-ant-camille', model: null }]);
    await expect(card.locator('[data-ai-state]')).toContainText(language === 'en' ? 'each task on its own model' : 'chaque tâche avec son modèle');
  });
}

/** What drafting asks for opportunity 42, at the interview stage: a follow-up. */
function asked42(en: boolean): unknown {
  return { projectId: 42, kind: 'FOLLOW_UP', language: en ? 'EN' : 'FR', notes: en ? 'Available in January' : 'Dispo en janvier' };
}
