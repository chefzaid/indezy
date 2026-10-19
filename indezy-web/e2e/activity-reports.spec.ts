import { Page, expect, test } from '@playwright/test';
import { mockWorkspaceSession } from './helpers/workspace';

interface Day { date: string; type: string; halfDay: boolean }

const holidays = [{ date: '2026-11-01', holiday: 'ALL_SAINTS' }, { date: '2026-11-11', holiday: 'ARMISTICE' }];

/** The month's report as the API answers it, totals computed from the days. */
function report(status: string, days: Day[], extra: Record<string, unknown> = {}): Record<string, unknown> {
  const sum = (filter: (day: Day) => boolean): number =>
    days.filter(filter).reduce((total, day) => total + (day.halfDay ? 0.5 : 1), 0);
  const worked = sum(day => day.type !== 'ABSENCE');
  return {
    id: days.length ? 7 : undefined, projectId: 42, year: 2026, month: 11, status, days, publicHolidays: holidays,
    businessDays: 20, workedDays: worked, onsiteDays: sum(day => day.type === 'ONSITE'),
    remoteDays: sum(day => day.type === 'REMOTE'), absenceDays: sum(day => day.type === 'ABSENCE'),
    dailyRate: 600, amount: worked * 600, missionStart: '2026-11-01', missionEnd: '2026-11-30', ...extra
  };
}

const texts = {
  en: {
    fill: 'Fill business days', halfRemote: 'Half day remote', submit: 'Send for approval', worked: 'Days worked',
    awaiting: 'Awaiting approval', copy: 'Copy link', thirteenth: /November 13, 2026/, page: 'Activity report approval',
    name: 'Your full name', comment: 'Comment (required to reject)', reject: 'Reject', reasonRequired: 'Give the reason for the rejection',
    approve: 'Approve the report', approved: 'Activity report approved', invalid: 'Invalid or expired link'
  },
  fr: {
    fill: 'Remplir les jours ouvrés', halfRemote: 'Demi-journée en télétravail', submit: 'Envoyer pour validation',
    worked: 'Jours travaillés', awaiting: 'En attente de validation', copy: 'Copier le lien', thirteenth: /13 novembre 2026/,
    page: 'Validation du compte rendu d\'activité', name: 'Votre nom et prénom', comment: 'Commentaire (obligatoire en cas de refus)',
    reject: 'Refuser', reasonRequired: 'Indiquez le motif du refus', approve: 'Valider le compte rendu',
    approved: 'Compte rendu validé', invalid: 'Lien invalide ou expiré'
  }
};

async function mockMission(page: Page, language: string): Promise<{ saved: () => Day[] }> {
  let savedDays: Day[] = [];
  let status = 'DRAFT';
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/projects/42', route => route.fulfill({ json: {
    id: 42, role: 'Java developer', status: 'WON', clientName: 'Société Générale', middlemanName: 'Capgemini',
    dailyRate: 600, workMode: 'HYBRID', startDate: '2026-11-01', durationInMonths: 1
  } }));
  await page.route('**/api/projects/42/notes', route => route.fulfill({ json: [] }));
  await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
  await page.route('**/api/projects/42/activity-reports', route =>
    route.fulfill({ json: savedDays.length ? [report(status, savedDays)] : [] }));
  await page.route('**/api/projects/42/activity-reports/2026/11', async route => {
    if (route.request().method() === 'PUT') {
      savedDays = route.request().postDataJSON().days;
    }
    await route.fulfill({ json: report(status, savedDays) });
  });
  await page.route('**/api/projects/42/activity-reports/2026/11/submit', route => {
    status = 'SUBMITTED';
    return route.fulfill({ json: {
      token: 'e2e-link-token', expiresAt: '2026-12-31T10:00:00Z', report: report(status, savedDays)
    } });
  });
  return { saved: () => savedDays };
}

for (const language of ['en', 'fr'] as const) {
  test(`records, submits and approves a monthly activity report (${language})`, async ({ page, context, browser }) => {
    test.setTimeout(90_000);
    const t = texts[language];
    if (language === 'fr') { await page.setViewportSize({ width: 390, height: 844 }); }
    await mockWorkspaceSession(page, language);
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    const mission = await mockMission(page, language);

    await page.goto('/projects/42');
    const card = page.locator('app-activity-report-card');
    await expect(card.getByRole('heading', { name: language === 'en' ? 'November 2026' : 'Novembre 2026' })).toBeVisible();
    await card.getByRole('button', { name: t.fill }).click();
    await card.getByRole('option', { name: t.halfRemote }).click();
    await card.getByRole('button', { name: t.thirteenth }).click();
    await expect(card.locator('[data-total="worked"]')).toHaveText(language === 'en' ? '19.5' : '19,5');
    await card.screenshot({ path: test.info().outputPath(`activity-report-${language}.png`) });

    await card.getByRole('button', { name: t.submit }).click();
    await expect(card.locator('[data-report-status]')).toHaveText(t.awaiting);
    expect(mission.saved()).toHaveLength(20);
    expect(mission.saved().find(day => day.date === '2026-11-13')).toEqual({ date: '2026-11-13', type: 'REMOTE', halfDay: true });
    expect(mission.saved().some(day => day.date === '2026-11-11')).toBe(false);
    await card.getByRole('button', { name: t.copy }).click();
    const link = await page.evaluate(() => navigator.clipboard.readText());
    expect(link).toBe('http://127.0.0.1:4200/activity-report#e2e-link-token');
    await expect(card.locator('[data-validation-link]')).toHaveValue(link);

    // The ESN opens the link in a browser without an Indezy session.
    const validatorContext = await browser.newContext(language === 'fr' ? { viewport: { width: 390, height: 844 } } : {});
    const validator = await validatorContext.newPage();
    await validator.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
    let decision: Record<string, unknown> | undefined;
    await validator.route('**/api/public/activity-reports', route => {
      expect(route.request().headers()['x-report-token']).toBe('e2e-link-token');
      expect(route.request().url()).not.toContain('e2e-link-token');
      return route.fulfill({ json: {
        consultantName: 'Workspace Test', clientName: 'Société Générale', middlemanName: 'Capgemini',
        missionRole: 'Java developer', showsLocation: true, report: report('SUBMITTED', mission.saved(), { id: undefined })
      } });
    });
    await validator.route('**/api/public/activity-reports/decision', route => {
      decision = route.request().postDataJSON();
      return route.fulfill({ json: {
        consultantName: 'Workspace Test', clientName: 'Société Générale', missionRole: 'Java developer', showsLocation: true,
        report: report('VALIDATED', mission.saved(), { validatorName: 'Jean Dupont', decidedAt: '2026-12-01T09:42:00Z' })
      } });
    });
    await validator.goto(link);
    await expect(validator.getByRole('heading', { name: t.page })).toBeVisible();
    await expect(validator.locator('[data-total="worked"]')).toHaveText(language === 'en' ? '19.5' : '19,5');
    await expect(validator.locator('app-activity-calendar button')).toHaveCount(0);

    await validator.getByLabel(t.name).fill('Jean Dupont');
    await validator.getByRole('button', { name: t.reject }).click();
    await expect(validator.getByText(t.reasonRequired)).toBeVisible();
    expect(decision).toBeUndefined();
    await validator.getByRole('button', { name: t.approve }).click();
    await expect(validator.getByRole('heading', { name: t.approved })).toBeVisible();
    expect(decision).toEqual({ approved: true, validatorName: 'Jean Dupont' });
    await validator.screenshot({ path: test.info().outputPath(`activity-report-approved-${language}.png`), fullPage: true });
    await validatorContext.close();
  });
}

test('explains an expired validation link and switches its language', async ({ page }) => {
  await page.addInitScript(() => {
    if (!sessionStorage.getItem('seeded')) {
      localStorage.setItem('indezy-lang', 'fr');
      sessionStorage.setItem('seeded', 'yes');
    }
  });
  await page.route('**/api/public/activity-reports', route => route.fulfill({ status: 404, json: {} }));
  await page.goto('/activity-report#expired-token');
  await expect(page.getByRole('heading', { name: texts.fr.invalid })).toBeVisible();
  await page.getByRole('button', { name: 'English' }).click();
  await expect(page.getByRole('heading', { name: texts.en.invalid })).toBeVisible();
  await expect(page).toHaveURL(/#expired-token$/);
});

test('saves an ESN activity report template from the client form', async ({ page }) => {
  await mockWorkspaceSession(page, 'en');
  await page.route('**/api/users/preferences', route => route.fulfill({ json: {} }));
  let created: Record<string, unknown> | undefined;
  await page.route('**/api/clients', route => {
    created = route.request().postDataJSON();
    return route.fulfill({ status: 201, json: { id: 9, ...created } });
  });
  await page.route('**/api/clients/9', route => route.fulfill({ json: { id: 9, companyName: 'Capgemini', city: 'Paris', isFinal: false } }));
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/contacts/by-client/9', route => route.fulfill({ json: [] }));

  await page.goto('/clients/new?type=esn');
  await page.getByLabel('Company name').fill('Capgemini');
  await page.getByLabel('City').fill('Paris');
  const template = page.locator('[data-activity-report-template]');
  await template.getByLabel('Document title').fill('Monthly timesheet');
  await template.getByRole('combobox', { name: 'Layout' }).click();
  await page.getByRole('option', { name: 'Month grid (landscape)' }).click();
  await template.getByRole('combobox', { name: 'PDF language' }).click();
  await page.getByRole('option', { name: 'English' }).click();
  await template.getByRole('switch', { name: 'Split on site and remote' }).click();
  await template.getByLabel('Footer note').fill('Return before the 25th');
  await page.getByRole('button', { name: 'Create' }).click();

  await expect(page).toHaveURL(/\/clients\/9$/);
  expect(created).toEqual(expect.objectContaining({
    companyName: 'Capgemini', isFinal: false, activityReportTitle: 'Monthly timesheet', activityReportLayout: 'CALENDAR_GRID',
    activityReportLanguage: 'EN', activityReportShowsLocation: false, activityReportFooter: 'Return before the 25th'
  }));
});
