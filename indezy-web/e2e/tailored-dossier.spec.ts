import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockFeed } from './helpers/offer-feed';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];
const REVIEW = { headline: { before: 'Développeur Java', after: 'Développeur Java' },
  summary: { before: 'Dix ans de Java.', after: 'Dix ans de Java et de Kafka.' }, skillsBefore: ['Java', 'Kafka'], skills: ['Kafka', 'Java'],
  missions: [{ number: 1, id: null, title: 'Lead Java', before: 'Migration vers Kafka.', after: 'Migration du SI vers Kafka.', stack: 'Java, Kafka' }],
  positions: [], coverage: { covered: ['Java', 'Kafka'], partial: [], gaps: ['Rust'] } };

for (const language of ['en', 'fr']) {
  test(`tailors the dossier to a feed offer and attaches it to the opportunity it becomes (${language})`, async ({ page }) => {
    const en = language === 'en';
    await mockFeed(page, language);
    const dossiers: string[] = [];
    await page.route(/\/api\/ai\/offers\/1\/dossier\/review/, route => {
      dossiers.push(new URL(route.request().url()).search);
      return route.fulfill({ json: REVIEW });
    });
    await page.route('**/api/ai/cv/pdf?*', route => route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' }));
    await page.route(/\/api\/ai\/offers\/1\/verdict/, route => route.fulfill({ status: 204, body: '' }));
    await page.route(/\/api\/ai\/offers\/1\/pack/, route => route.fulfill({ json: { subject: 'Candidature', message: 'Bonjour,',
      screening: [], rate: 0, rateReasons: [] } }));
    await page.route(/\/api\/offers\/1\/import$/, route => route.fulfill({ json: { id: 42, role: 'Lead Kotlin' } }));
    await page.route(/\/api\/projects\/42\/notes$/, route => route.fulfill({ json: { id: 1, projectId: 42, content: '', createdAt: '2026-11-28T10:00:00' } }));
    const attached: string[] = [];
    await page.route(/\/api\/projects\/42\/attachments$/, route => {
      attached.push(route.request().postDataBuffer()?.toString('latin1') ?? '');
      return route.fulfill({ json: { id: 3, fileName: 'dossier.pdf', contentType: 'application/pdf', sizeBytes: 8, createdAt: '2026-11-28T10:00:00' } });
    });

    await page.route(/\/api\/users\/cv-profiles\/closest/, route => route.fulfill({ json: { id: 4, name: 'Backend Kafka', isDefault: true,
      language: 'FR', headline: null, summary: null, skills: null, missions: null, positions: null, updatedAt: null } }));
    await page.goto('/offers/1');
    const pack = page.locator('[data-offer-pack]');
    // The CV profile closest to the offer, until a dossier is tailored to it.
    await expect(pack.locator('[data-offer-pack-cv]')).toContainText('Backend Kafka');
    // Tailored to the offer without preparing the pack nor adding the offer to the pipeline.
    await pack.locator('[data-dossier="offer"]').click();
    await page.locator('[data-dossier-choice="FR"]').click();
    await expect(page.locator('[data-ai-cv-review]')).toBeVisible();
    expect(dossiers).toEqual(['?language=FR']);
    await page.locator('[data-ai-cv-download]').click();
    const download = page.waitForEvent('download');
    await page.locator('[data-ai-cv-pdf="ANONYMOUS"]').click();
    expect((await download).suggestedFilename()).toBe('dossier-competences-anonymous-tailored.pdf');
    await page.locator('[data-ai-cv-close]').click();
    await expect(page.locator('mat-dialog-container')).toHaveCount(0);

    await pack.locator('[data-offer-pack-write]').click();
    await expect(pack.locator('[data-offer-pack-dossier]')).toContainText('dossier-competences-anonymous-tailored.pdf');
    const audit = await new AxeBuilder({ page }).include('[data-offer-pack]').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await pack.locator('[data-offer-pack-add]').click();
    await expect.poll(() => attached.length).toBe(1);
    expect(attached[0]).toContain('filename="dossier-competences-anonymous-tailored.pdf"');
    expect(attached[0]).toContain('%PDF-1.7');
    await expect(page.getByText(en ? 'Added to the pipeline' : 'Ajoutée au pipeline', { exact: false }).first()).toBeAttached();
  });

  test(`tailors the dossier to a pasted job posting from the profile (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    const asked: { search: string; body: unknown }[] = [];
    await page.route('**/api/ai/dossier/posting/review?*', route => {
      asked.push({ search: new URL(route.request().url()).search, body: route.request().postDataJSON() });
      return route.fulfill({ json: REVIEW });
    });
    const refined: { text: string; refinement: string; focus: string | null; locked: string[] }[] = [];
    await page.route('**/api/ai/cv/refine', route => {
      refined.push(route.request().postDataJSON());
      return route.fulfill({ json: { text: 'Migration vers Kafka, au cœur du SI.' } });
    });
    let reviewed: { missions: unknown[] } | null = null;
    await page.route('**/api/ai/cv/pdf?*', route => {
      reviewed = route.request().postDataJSON();
      return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
    });

    await page.goto('/profile');
    await page.locator('[data-dossier="profile"]').click();
    await page.locator('[data-dossier-posting]').click();
    await page.locator('[data-posting-dossier-text]').fill('Mission Kafka chez un assureur, 12 mois.');
    await page.locator('[data-posting-dossier-language]').getByRole('radio', { name: 'English' }).click();
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await page.locator('[data-posting-dossier-make]').click();
    const review = page.locator('[data-ai-cv-review]');
    await expect(review).toBeVisible();
    expect(asked).toEqual([{ search: '?language=EN', body: { text: 'Mission Kafka chez un assureur, 12 mois.' } }]);

    // What the posting asks for, and what the rewording states that the profile does not ("SI").
    await expect(review.locator('[data-ai-cv-gaps]')).toContainText('Rust');
    await expect(review.locator('[data-ai-cv-fact="mission-1"]')).toContainText('SI');

    // The mission refined in one click, bringing Kubernetes out and keeping "Kafka" as written; then the summary locked.
    // The dialog moves focus to its first field once open: type after that.
    await expect(review.locator('[data-ai-cv-focus]')).toBeFocused();
    await review.locator('[data-ai-cv-focus]').fill('Kubernetes');
    await review.locator('[data-ai-cv-phrase-input]').fill('Kafka');
    await review.locator('[data-ai-cv-phrase-input]').press('Enter');
    await expect(review.locator('[data-ai-cv-phrase]')).toHaveCount(1);
    await review.locator('[data-ai-cv-refine="mission-1"]').click();
    await page.locator('[data-ai-cv-refinement="HIGHLIGHT"]').click();
    await expect(review.locator('[data-ai-cv-text="mission-1"]')).toHaveValue('Migration vers Kafka, au cœur du SI.');
    expect(refined).toEqual([{ text: 'Migration du SI vers Kafka.', refinement: 'HIGHLIGHT', focus: 'Kubernetes', locked: ['Kafka'], language: 'EN' }]);
    // A question on the vague mission, answered and woven in.
    await page.route('**/api/ai/cv/questions', route => route.fulfill({ json: [{ key: 'mission-1', question: 'How many services?' }] }));
    await review.locator('[data-ai-cv-ask]').click();
    await review.locator('[data-ai-cv-answer="mission-1"]').fill('12 services');
    await review.locator('[data-ai-cv-weave="mission-1"]').click();
    await expect(review.locator('[data-ai-cv-question="mission-1"]')).toHaveCount(0);
    expect(refined[1]).toEqual(expect.objectContaining({ refinement: 'WEAVE', answer: '12 services' }));
    await review.locator('[data-ai-cv-lock="summary"]').click();
    await expect(review.locator('[data-ai-cv-refine="summary"]')).toBeDisabled();
    await expect(review.locator('[data-ai-cv-text="summary"]')).not.toBeEditable();
    const reviewAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(reviewAudit.violations.map(violation => violation.id)).toEqual([]);

    await page.locator('[data-ai-cv-download]').click();
    const download = page.waitForEvent('download');
    await page.locator('[data-ai-cv-pdf="ANONYMOUS"]').click();
    expect((await download).suggestedFilename()).toBe('skills-profile-anonymous-tailored.pdf');
    expect(reviewed!.missions).toEqual([{ number: 1, description: 'Migration vers Kafka, au cœur du SI.' }]);
  });
}
