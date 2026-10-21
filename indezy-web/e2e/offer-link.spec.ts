import { expect, Page, test } from '@playwright/test';
import { emptyDashboardStats, mockWorkspaceSession } from './helpers/workspace';
import { offer } from './helpers/offer-feed';

const LINK = 'https://www.free-work.com/fr/tech-it/job-mission/java/7?utm_source=alerte';
const KNOWN = 'https://www.free-work.com/fr/tech-it/job-mission/java/8';

const texts = {
  en: { fromLink: 'From an offer link', label: 'Offer link', fill: 'Fill in', reading: 'Reading Free-Work to find this offer',
    newEsn: 'Exemple Conseil (new ESN, created on save)', save: 'Create', open: 'Open the opportunity', role: 'Role / Position' },
  fr: { fromLink: 'Depuis le lien d\'une offre', label: 'Lien de l\'offre', fill: 'Préremplir',
    reading: 'Lecture de Free-Work pour trouver cette offre', newEsn: 'Exemple Conseil (nouvelle ESN, créée à l\'enregistrement)',
    save: 'Créer', open: 'Ouvrir l\'opportunité', role: 'Rôle / Poste' }
};

/** The workspace's board, clients and sources; the link of offer 7 is read once, the one of offer 8 is in the pipeline. */
async function mockWorkspace(page: Page, language: string): Promise<{ imported: unknown[] }> {
  const imported: unknown[] = [];
  await mockWorkspaceSession(page, language);
  await page.route('**/api/users/preferences', route => route.fulfill({ json: { theme: 'light', language } }));
  await page.route('**/api/users/profile', route => route.fulfill({ json: { id: 1, skills: [] } }));
  await page.route('**/api/freelances/1', route => route.fulfill({ json: { id: 1 } }));
  await page.route('**/api/projects/stats/dashboard/1*', route => route.fulfill({ json: emptyDashboardStats }));
  await page.route('**/api/projects/kanban/1*', route => route.fulfill({ json: { columnOrder: ['CONTACT'], columns: { CONTACT: [] } } }));
  await page.route('**/api/projects/by-freelance/1', route => route.fulfill({ json: [] }));
  await page.route('**/api/clients/by-freelance/1', route => route.fulfill({ json: [
    { id: 3, companyName: 'Airbus', city: 'Toulouse', isFinal: true }
  ] }));
  await page.route('**/api/sources/by-freelance/1', route => route.fulfill({ json: [{ id: 5, name: 'Free-Work', type: 'JOB_BOARD' }] }));
  await page.route('**/api/seasons/by-freelance/1', route => route.fulfill({ json: [] }));
  let reads = 0;
  await page.route('**/api/offers/link', async route => {
    const url = (route.request().postDataJSON() as { url: string }).url;
    if (url === KNOWN) {
      await route.fulfill({ json: { status: 'IN_PIPELINE', siteName: 'Free-Work', offer: offer(8), projectId: 40 } });
      return;
    }
    reads++;
    await route.fulfill({ json: reads === 1 ? { status: 'READING', siteName: 'Free-Work', retryAt: new Date().toISOString() } : {
      status: 'FOUND', siteName: 'Free-Work', offer: offer(7),
      draft: { clientName: 'Exemple Conseil', sourceName: 'Free-Work', project: {
        role: 'Développeur Java 7', status: 'CONTACT', dailyRate: 650, techStack: 'Java, Spring Boot, Kafka', workMode: 'HYBRID',
        remoteDaysPerMonth: 9, startDate: '2026-11-02', durationInMonths: 6, link: offer(7).url, sourceId: 5, freelanceId: 1
      } }
    } });
  });
  await page.route('**/api/offers/7/import', async route => {
    imported.push(route.request().postDataJSON());
    await route.fulfill({ status: 201, json: { id: 42, role: 'Tech lead Java', status: 'CONTACT' } });
  });
  for (const id of [40, 42]) {
    await page.route(`**/api/projects/${id}`, route => route.fulfill({ json: { id, role: 'Tech lead Java', status: 'CONTACT', dailyRate: 650 } }));
    await page.route(`**/api/projects/${id}/notes`, route => route.fulfill({ json: [] }));
  }
  await page.route('**/api/interview-steps/**', route => route.fulfill({ json: [] }));
  return { imported };
}

for (const language of ['en', 'fr'] as const) {
  test(`makes an opportunity from an offer link (${language})`, async ({ page }) => {
    test.setTimeout(60_000);
    if (language === 'fr') { await page.setViewportSize({ width: 390, height: 844 }); }
    const t = texts[language];
    const { imported } = await mockWorkspace(page, language);

    await page.goto('/dashboard');
    await page.getByRole('radio', { name: /Kanban/ }).click();
    await page.getByRole('link', { name: t.fromLink }).click();
    await expect(page).toHaveURL(/\/projects\/new\?from=link$/);
    const field = page.getByRole('textbox', { name: t.label });
    await expect(field).toBeFocused();

    await field.fill(LINK);
    await field.press('Enter');
    await expect(page.getByText(t.reading)).toBeVisible();
    // Looked up again five seconds later, once the site was read.
    await expect(page.getByRole('textbox', { name: t.role, exact: true })).toHaveValue('Développeur Java 7');
    await expect(page.locator('[data-from-offer]')).toContainText('Développeur Java 7');
    await expect(page.locator('mat-select[formcontrolname="clientId"]')).toContainText(t.newEsn);
    await page.locator('.form-card').screenshot({ path: test.info().outputPath('offer-link.png') });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

    await page.getByRole('textbox', { name: t.role, exact: true }).fill('Tech lead Java');
    await page.getByRole('button', { name: t.save, exact: true }).click();
    await expect(page).toHaveURL(/\/projects\/42$/);
    expect(imported).toEqual([expect.objectContaining({ role: 'Tech lead Java', dailyRate: 650, sourceId: 5, status: 'CONTACT' })]);
    expect(imported[0]).not.toHaveProperty('clientId', -1);

    // A mission already in the pipeline opens its opportunity instead.
    await page.goto('/projects');
    await page.getByRole('link', { name: t.fromLink }).click();
    await page.getByRole('textbox', { name: t.label }).fill(KNOWN);
    await page.getByRole('button', { name: t.fill }).click();
    await page.getByRole('link', { name: t.open }).click();
    await expect(page).toHaveURL(/\/projects\/40$/);
  });
}

test('makes an opportunity from a posting of a site the feed does not collect (en)', async ({ page }) => {
  test.setTimeout(60_000);
  const posting = 'https://www.acme-careers.fr/postes/developpeur-java';
  const { imported } = await mockWorkspace(page, 'en');
  const postings: unknown[] = [];
  // Registered last, so it answers instead of the workspace's route: the page's posting, read and stored nowhere.
  await page.route('**/api/offers/link', route => route.fulfill({ json: { status: 'POSTING', siteName: 'acme-careers.fr', draft: {
    clientName: 'Acme', sourceName: 'acme-careers.fr', sourceLink: 'https://www.acme-careers.fr',
    project: { role: 'Développeur Java', status: 'CONTACT', dailyRate: 600, link: posting, freelanceId: 1 } } } }));
  await page.route('**/api/offers/postings/import', async route => {
    postings.push(route.request().postDataJSON());
    await route.fulfill({ status: 201, json: { id: 42, role: 'Développeur Java', status: 'CONTACT' } });
  });

  await page.goto('/projects/new?from=link');
  const field = page.getByRole('textbox', { name: texts.en.label });
  await field.fill(posting);
  await field.press('Enter');

  await expect(page.getByText('Read from the page on acme-careers.fr, which the feed does not collect')).toBeVisible();
  await expect(page.getByRole('textbox', { name: texts.en.role, exact: true })).toHaveValue('Développeur Java');
  await expect(page.locator('[data-from-offer]')).toContainText('Développeur Java');
  await expect(page.locator('mat-select[formcontrolname="clientId"]')).toContainText('Acme (new ESN, created on save)');
  await page.getByRole('button', { name: texts.en.save, exact: true }).click();

  await expect(page).toHaveURL(/\/projects\/42$/);
  expect(postings).toEqual([expect.objectContaining({ clientName: 'Acme', sourceName: 'acme-careers.fr',
    sourceLink: 'https://www.acme-careers.fr', project: expect.objectContaining({ role: 'Développeur Java', dailyRate: 600 }) })]);
  expect(imported).toEqual([]);
});
