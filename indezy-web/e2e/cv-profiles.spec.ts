import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

for (const language of ['en', 'fr']) {
  test(`keeps CV profiles, each drawn as its own dossier, one the default (${language})`, async ({ page }) => {
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    const profiles: Record<string, unknown>[] = [];
    const created: Record<string, unknown>[] = [];
    await page.route(/\/api\/users\/cv-profiles$/, route => {
      if (route.request().method() === 'POST') {
        const body = route.request().postDataJSON();
        created.push(body);
        const profile = { ...body, id: 7 + profiles.length, isDefault: profiles.length === 0, updatedAt: '2026-11-28T10:00:00' };
        profiles.push(profile);
        return route.fulfill({ status: 201, json: profile });
      }
      return route.fulfill({ json: profiles });
    });
    await page.route('**/api/users/cv-profiles/sources', route => route.fulfill({ json: { headline: 'Java developer', summary: 'Ten years of Java.',
      skills: ['Java', 'Kafka', 'Angular'], missions: [{ id: 42, title: 'Lead Kafka', description: 'Payroll flows.' }], positions: [] } }));
    const drawn: string[] = [];
    await page.route(/\/api\/users\/cv-profiles\/7\/dossier/, route => {
      drawn.push(new URL(route.request().url()).search);
      return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
    });

    await page.goto('/profile');
    const card = page.locator('[data-cv-profiles]');
    await expect(card.locator('[data-no-cv-profile]')).toBeVisible();
    await card.locator('[data-cv-profile-add]').click();
    const dialog = page.locator('mat-dialog-container');
    await dialog.locator('[data-cv-profile-name]').fill('Backend Kafka');
    await dialog.locator('[data-cv-profile-headline]').fill('Lead Kafka');
    await dialog.locator('[data-cv-profile-row="skill-Angular"] input').uncheck();
    await dialog.locator('[data-cv-profile-text="mission-42"]').fill('Real-time payroll flows.');
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-cv-profile-save]').click();
    await expect(dialog).toHaveCount(0);
    expect(created).toEqual([{ name: 'Backend Kafka', language: 'FR', headline: 'Lead Kafka', summary: null, skills: ['Java', 'Kafka'],
      missions: [{ id: 42, text: 'Real-time payroll flows.' }], positions: null }]);

    await expect(card.locator('[data-cv-profile="7"]')).toContainText('Backend Kafka');
    await card.locator('[data-cv-profile-menu="7"]').click();
    const download = page.waitForEvent('download');
    await page.locator('[data-cv-profile-download="ANONYMOUS-DOCX"]').click();
    expect((await download).suggestedFilename()).toBe('cv-backend-kafka-anonymous.docx');
    expect(drawn).toEqual(['?layout=ANONYMOUS&format=DOCX']);
    // Its English twin, written by AI and listed right after it.
    await page.route(/\/api\/users\/cv-profiles\/7\/translate$/, route => {
      const twin = { ...profiles[0], id: 8, name: 'Backend Kafka (EN)', language: 'EN', isDefault: false, twinId: 7 };
      profiles[0] = { ...profiles[0], twinId: 8 };
      profiles.push(twin);
      return route.fulfill({ json: twin });
    });
    await card.locator('[data-cv-profile-menu="7"]').click();
    await page.locator('[data-cv-profile-translate]').click();
    await expect(card.locator('[data-cv-profile="8"]')).toContainText('Backend Kafka (EN)');
    await expect(card.locator('[data-cv-profile-twin="7"]')).toContainText('Backend Kafka (EN)');

    // A fact its wording relies on changed: said, then marked reviewed.
    profiles[0] = { ...profiles[0], changes: [{ kind: 'ITEM_CHANGED', label: 'Lead Kafka', fields: ['dates'] }] };
    let reviewed = 0;
    await page.route(/\/api\/users\/cv-profiles\/7\/reviewed$/, route => {
      reviewed++;
      profiles[0] = { ...profiles[0], changes: [] };
      return route.fulfill({ json: profiles[0] });
    });
    await page.reload();
    const review = card.locator('[data-cv-profile-review="7"]');
    await expect(review).toContainText(language === 'en' ? 'Lead Kafka: its dates changed' : 'Lead Kafka : dates modifié(s)');
    const reviewAudit = await new AxeBuilder({ page }).include('[data-cv-profiles]').withTags(WCAG).analyze();
    expect(reviewAudit.violations.map(violation => violation.id)).toEqual([]);
    await review.locator('[data-cv-profile-reviewed="7"]').click();
    await expect(review).toHaveCount(0);
    expect(reviewed).toBe(1);

    const cardAudit = await new AxeBuilder({ page }).include('[data-cv-profiles]').withTags(WCAG).analyze();
    expect(cardAudit.violations.map(violation => violation.id)).toEqual([]);
  });
}
