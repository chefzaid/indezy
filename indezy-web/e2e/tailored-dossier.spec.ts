import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockFeed } from './helpers/offer-feed';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

for (const language of ['en', 'fr']) {
  test(`tailors the dossier to a feed offer and attaches it to the opportunity it becomes (${language})`, async ({ page }) => {
    const en = language === 'en';
    await mockFeed(page, language);
    const dossiers: string[] = [];
    await page.route(/\/api\/ai\/offers\/1\/dossier/, route => {
      dossiers.push(new URL(route.request().url()).search);
      return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
    });
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

    await page.goto('/offers/1');
    const pack = page.locator('[data-offer-pack]');
    // Tailored to the offer without preparing the pack nor adding the offer to the pipeline.
    await pack.locator('[data-dossier="offer"]').click();
    const download = page.waitForEvent('download');
    await page.locator('[data-dossier-choice="ANONYMOUS-FR"]').click();
    expect((await download).suggestedFilename()).toBe('dossier-competences-anonymous-tailored.pdf');
    expect(dossiers).toEqual(['?layout=ANONYMOUS&language=FR']);

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
    await page.route('**/api/ai/dossier/posting?*', route => {
      asked.push({ search: new URL(route.request().url()).search, body: route.request().postDataJSON() });
      return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
    });

    await page.goto('/profile');
    await page.locator('[data-dossier="profile"]').click();
    await page.locator('[data-dossier-posting]').click();
    await page.locator('[data-posting-dossier-text]').fill('Mission Kafka chez un assureur, 12 mois.');
    await page.locator('[data-posting-dossier-layout]').getByRole('radio').nth(1).click();
    await page.locator('[data-posting-dossier-language]').getByRole('radio', { name: 'English' }).click();
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    const download = page.waitForEvent('download');
    await page.locator('[data-posting-dossier-make]').click();
    expect((await download).suggestedFilename()).toBe('skills-profile-anonymous-tailored.pdf');
    expect(asked).toEqual([{ search: '?layout=ANONYMOUS&language=EN', body: { text: 'Mission Kafka chez un assureur, 12 mois.' } }]);
  });
}
