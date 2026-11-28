import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

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
    // Compact, in blue, previewed before saving.
    const previewed: Record<string, unknown>[] = [];
    await page.route('**/api/users/cv-profiles/preview*', route => {
      previewed.push(route.request().postDataJSON());
      return route.fulfill({ contentType: 'image/png', body: Buffer.from(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=', 'base64') });
    });
    await dialog.locator('[data-cv-profile-style]').getByRole('radio').nth(1).click();
    await dialog.locator('[data-cv-profile-accent]').getByRole('radio').nth(1).click();
    await dialog.locator('[data-cv-profile-preview]').click();
    await expect(dialog.locator('[data-cv-profile-preview-image]')).toBeVisible();
    expect(previewed[0]).toEqual(expect.objectContaining({ style: 'COMPACT', accent: '#1A73E8' }));
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-cv-profile-save]').click();
    await expect(dialog).toHaveCount(0);
    expect(created).toEqual([{ name: 'Backend Kafka', language: 'FR', headline: 'Lead Kafka', summary: null, skills: ['Java', 'Kafka'],
      missions: [{ id: 42, text: 'Real-time payroll flows.' }], positions: null, style: 'COMPACT', accent: '#1A73E8' }]);

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

    // Archived, it waits apart until brought back.
    await page.route(/\/api\/users\/cv-profiles\/8\/archived$/, route => {
      const archived = route.request().postDataJSON().archived;
      profiles[1] = { ...profiles[1], archived };
      return route.fulfill({ json: profiles[1] });
    });
    await card.locator('[data-cv-profile-menu="8"]').click();
    await page.locator('[data-cv-profile-archive]').click();
    await expect(card.locator('[data-cv-profile="8"]')).toHaveCount(0);
    await card.locator('[data-cv-profiles-archived-toggle]').click();
    await card.locator('[data-cv-profile-restore="8"]').click();
    await expect(card.locator('[data-cv-profile="8"]')).toBeVisible();

    // Checked before it goes out: an anonymized CV that still names its client says so.
    await page.route(/\/api\/users\/cv-profiles\/7\/audit/, route => route.fulfill({ json: { pages: 2, findings: [
      { check: 'LEAK', severity: 'ERROR', block: 'Lead Kafka', detail: 'Acme' }, { check: 'NO_FIGURE', severity: 'INFO', block: 'Lead Kafka', detail: null }] } }));
    await card.locator('[data-cv-profile-menu="7"]').click();
    await page.locator('[data-cv-profile-audit]').click();
    const auditDialog = page.locator('mat-dialog-container');
    await expect(auditDialog.locator('[data-cv-audit-finding="LEAK"]')).toContainText('Acme');
    // Measured once the dialog has faded in, not while its colours are still transparent.
    await settleDialog(page);
    const checkAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(checkAudit.violations.map(violation => violation.id)).toEqual([]);
    await auditDialog.getByRole('button', { name: language === 'en' ? 'Close' : 'Fermer' }).click();
    await expect(auditDialog).toHaveCount(0);

    // Shared by a link of its own, shown once.
    const shared: unknown[] = [];
    await page.route(/\/api\/users\/cv-shares$/, route => {
      if (route.request().method() === 'POST') {
        shared.push(route.request().postDataJSON());
        return route.fulfill({ status: 201, json: { token: 'e2e-cv-token', share: { id: 1, profileId: 7, profileName: 'Backend Kafka',
          recipient: 'ESN Alpha', layout: 'ANONYMOUS', createdAt: '2026-11-28T08:00:00', expiresAt: '2026-12-05T08:00:00Z', revokedAt: null,
          active: true, openCount: 0, downloadCount: 0, lastOpenedAt: null } } });
      }
      return route.fulfill({ json: [] });
    });
    await card.locator('[data-cv-profile-menu="7"]').click();
    await page.locator('[data-cv-profile-share]').click();
    const shareDialog = page.locator('mat-dialog-container');
    await settleDialog(page);
    await shareDialog.locator('[data-cv-share-recipient]').fill('ESN Alpha');
    await shareDialog.locator('[data-cv-share-layout]').getByRole('radio').nth(1).click();
    await shareDialog.locator('[data-cv-share-create]').click();
    await expect(shareDialog.locator('[data-cv-share-url]')).toHaveValue(/\/shared-cv#e2e-cv-token$/);
    expect(shared).toEqual([{ profileId: 7, recipient: 'ESN Alpha', layout: 'ANONYMOUS', days: 7 }]);
    const shareAudit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(shareAudit.violations.map(violation => violation.id)).toEqual([]);
    await shareDialog.getByRole('button', { name: language === 'en' ? 'Close' : 'Fermer' }).click();
    await expect(shareDialog).toHaveCount(0);

    const cardAudit = await new AxeBuilder({ page }).include('[data-cv-profiles]').withTags(WCAG).analyze();
    expect(cardAudit.violations.map(violation => violation.id)).toEqual([]);
  });
}

test('opens a shared CV without an account, and says when the link no longer opens', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const tokens: (string | undefined)[] = [];
  await page.route('**/api/public/cv-shares', route => {
    const token = route.request().headers()['x-cv-token'];
    tokens.push(token);
    return token === 'good' ? route.fulfill({ json: { name: 'A. M.', headline: 'Lead Kafka', language: 'FR', layout: 'ANONYMOUS',
      expiresAt: '2026-12-05T08:00:00Z' } }) : route.fulfill({ status: 404, json: { message: 'Link invalid or expired' } });
  });
  const formats: string[] = [];
  await page.route('**/api/public/cv-shares/dossier*', route => {
    formats.push(new URL(route.request().url()).search);
    return route.fulfill({ body: '%PDF-1.7', contentType: 'application/pdf' });
  });

  await page.goto('/shared-cv#good');
  await expect(page.locator('[data-shared-cv-name]')).toHaveText('A. M.');
  await expect(page.locator('main')).toContainText('Lead Kafka');
  const download = page.waitForEvent('download');
  await page.locator('[data-shared-cv-docx]').click();
  expect((await download).suggestedFilename()).toBe('cv-a-m.docx');
  expect(formats).toEqual(['?format=DOCX']);
  const audit = await new AxeBuilder({ page }).withTags(WCAG).analyze();
  expect(audit.violations.map(violation => violation.id)).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  // The token never goes in an address.
  expect(page.url()).toContain('#good');

  // A new link is a new page: changing only what follows # would not reload it.
  await page.goto('about:blank');
  await page.goto('/shared-cv#gone');
  await expect(page.locator('[data-shared-cv-invalid]')).toBeVisible();
  expect(tokens).toEqual(['good', 'gone']);
});

