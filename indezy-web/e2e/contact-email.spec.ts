import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';
import { mockWorkspaceData } from './helpers/workspace-data';
import { settleDialog } from './helpers/dialog';

const WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'];

for (const [language, width] of [['en', 1280], ['fr', 390]] as const) {
  test(`emails a contact from Indezy with open tracking and shows it opened (${language}, ${width}px)`, async ({ page }) => {
    const en = language === 'en';
    await page.setViewportSize({ width, height: 900 });
    await mockWorkspaceSession(page, language);
    await mockWorkspaceData(page, language);
    await page.route('**/api/contacts/emails/status', route => route.fulfill({ json: { available: true, sentToday: 2, dailyMax: 50 } }));
    const sent: unknown[] = [];
    await page.route('**/api/contacts/2/emails', route => {
      sent.push(route.request().postDataJSON());
      return route.fulfill({ json: { id: 31, contactId: 2, occurredOn: '2026-11-22', channel: 'EMAIL', direction: 'OUTGOING',
        summary: 'Java developer', email: { sentAt: '2026-11-22T10:00:00Z', tracked: true, opens: 0 } } });
    });

    await page.goto('/projects/42');
    await page.locator('app-project-email-button button').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('input[formcontrolname="subject"]').fill(en ? 'Java developer mission' : 'Mission développeur Java');
    await dialog.locator('textarea[formcontrolname="body"]').fill(en ? 'Hello Alice,\n\nWhere does it stand?' : 'Bonjour Alice,\n\nOù en est-on ?');
    await expect(dialog.locator('[data-email-send]')).toBeEnabled();
    await dialog.locator('[data-email-track] input').check();
    await settleDialog(page);
    const audit = await new AxeBuilder({ page }).include('mat-dialog-container').withTags(WCAG).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await dialog.locator('[data-email-send]').click();
    await expect(dialog).toHaveCount(0);
    expect(sent[0]).toEqual(expect.objectContaining({ projectId: 42, track: true, copy: true, language: en ? 'EN' : 'FR' }));

    await page.route(/\/api\/contacts\/2\/interactions$/, route => route.fulfill({ json: { interactions: [{ id: 31, contactId: 2,
      occurredOn: '2026-11-22', channel: 'EMAIL', direction: 'OUTGOING', summary: 'Java developer',
      email: { sentAt: '2026-11-22T10:00:00Z', tracked: true, openedAt: '2026-11-22T11:00:00Z', opens: 2 } }], lastContactedOn: '2026-11-22',
      asked: 0, answered: 0 } }));
    await page.goto('/contacts/2');
    await expect(page.locator('[data-sent-email]')).toContainText(en ? 'Opened 2×' : 'Ouvert 2 fois');
    const listAudit = await new AxeBuilder({ page }).include('.interactions-card').withTags(WCAG).analyze();
    expect(listAudit.violations.map(violation => violation.id)).toEqual([]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  });
}
