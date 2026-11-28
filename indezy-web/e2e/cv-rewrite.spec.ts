import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

for (const language of ['en', 'fr']) {
  test(`rewrites the whole CV with AI, then downloads and applies the blocks kept (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    const asked: unknown[] = [];
    await page.route('**/api/ai/cv/rewrite', route => {
      asked.push(route.request().postDataJSON());
      return route.fulfill({ json: {
        headline: { before: 'Développeur Java', after: 'Lead Java / Kafka' },
        summary: { before: 'Dix ans de Java.', after: 'Expert Java et Kafka, dix ans de SI bancaires.' },
        skillsBefore: ['Java', 'Kafka'], skills: ['Kafka', 'Java'],
        missions: [{ number: 1, id: null, title: 'Lead Java', before: 'Migration vers Kafka.', after: 'Migration du SI vers Kafka.' }],
        positions: [{ number: 1, id: 70, title: 'Développeur', before: 'Paie en Java.', after: 'Logiciel de paie en Java.' }]
      } });
    });
    const pdf: { search: string; body: unknown }[] = [];
    await page.route('**/api/ai/cv/pdf?*', route => {
      pdf.push({ search: new URL(route.request().url()).search, body: route.request().postDataJSON() });
      return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
    });
    const approved: unknown[] = [];
    await page.route('**/api/ai/cv/profile', route => {
      approved.push(route.request().postDataJSON());
      return route.fulfill({ status: 204 });
    });

    await page.goto('/profile');
    await page.locator('[data-ai-cv-open]').click();
    await page.locator('[data-ai-cv-positioning]').fill('Lead Java, Kafka');
    await page.locator('[data-ai-cv-rewrite]').click();
    await expect(page.locator('[data-ai-cv-review]')).toBeVisible();
    expect(asked).toEqual([{ positioning: 'Lead Java, Kafka', style: 'IMPACT', language: 'FR' }]);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);

    await page.locator('[data-ai-cv-keep="summary"] input').uncheck();
    await page.locator('[data-ai-cv-text="headline"]').fill('Staff Java');
    await page.locator('[data-ai-cv-download]').click();
    const download = page.waitForEvent('download');
    await page.locator('[data-ai-cv-pdf="ANONYMOUS"]').click();
    expect((await download).suggestedFilename()).toBe('dossier-competences-anonymous.pdf');
    expect(pdf).toEqual([{ search: '?layout=ANONYMOUS&language=FR', body: { headline: 'Staff Java', summary: null, skills: ['Kafka', 'Java'],
      missions: [{ number: 1, description: 'Migration du SI vers Kafka.' }], positions: [{ number: 1, description: 'Logiciel de paie en Java.' }] } }]);

    await page.locator('[data-ai-cv-approve]').click();
    await expect(page.locator('mat-dialog-container')).toHaveCount(0);
    expect(approved).toEqual([{ headline: 'Staff Java', summary: null, positions: [{ id: 70, description: 'Logiciel de paie en Java.' }] }]);
  });
}
