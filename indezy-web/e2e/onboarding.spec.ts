import { expect, Page, test } from '@playwright/test';
import { mockWorkspaceData } from './helpers/workspace-data';

const profile = { id: 1, firstName: 'Jean', lastName: 'Dupont', email: 'jean@example.test', skills: [], languages: [] };

/** A registration answered with a new account, whose welcome steps are pending. */
async function registerNewAccount(page: Page, language: 'en' | 'fr'): Promise<{ profiles: unknown[]; templates: unknown[]; completed: number[] }> {
  const calls = { profiles: [] as unknown[], templates: [] as unknown[], completed: [] as number[] };
  await page.addInitScript(lang => localStorage.setItem('indezy-lang', lang), language);
  await mockWorkspaceData(page, language);
  const payload = Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64');
  await page.route('**/api/auth/register', route => route.fulfill({ json: {
    token: `header.${payload}.browser-test`,
    user: { id: 1, accountId: 1, email: 'jean@example.test', firstName: 'Jean', lastName: 'Dupont', onboardingPending: true }
  } }));
  await page.route('**/api/users/profile', route => {
    if (route.request().method() === 'PUT') {
      calls.profiles.push(route.request().postDataJSON());
      return route.fulfill({ json: route.request().postDataJSON() });
    }
    return route.fulfill({ json: profile });
  });
  await page.route('**/api/pipeline-templates/steps', route => {
    calls.templates.push(route.request().postDataJSON());
    return route.fulfill({ status: 201, json: { id: 5, ...route.request().postDataJSON() } });
  });
  await page.route('**/api/users/onboarding/complete', route => {
    calls.completed.push(Date.now());
    return route.fulfill({ status: 204 });
  });

  await page.goto('/register');
  await page.locator('input[formcontrolname="firstName"]').fill('Jean');
  await page.locator('input[formcontrolname="lastName"]').fill('Dupont');
  await page.locator('input[formcontrolname="email"]').fill('jean@example.test');
  await page.locator('input[formcontrolname="password"]').fill('Password123!');
  await page.locator('input[formcontrolname="confirmPassword"]').fill('Password123!');
  await page.locator('form.register-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/welcome$/);
  return calls;
}

test('welcomes a new account step by step and opens its first opportunity form (en, desktop)', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  const calls = await registerNewAccount(page, 'en');

  await expect(page.getByRole('heading', { name: 'Welcome to Indezy, Jean' })).toBeVisible();
  await page.getByLabel('Title').fill('Java lead');
  const skills = page.locator('[data-skill-input]');
  await skills.fill('Java');
  await skills.press('Enter');
  await skills.fill('Kafka');
  await skills.press(',');
  await expect(page.locator('[data-skills] mat-chip')).toHaveText([/^\s*Java/, /^\s*Kafka/]);
  await page.getByLabel('Address').fill('1 rue de Rivoli');
  await page.getByLabel('City').fill('Paris');
  await page.screenshot({ path: test.info().outputPath('onboarding-about.png') });
  await page.locator('[data-about-next]').click();

  await page.getByLabel('Target daily rate').fill('650');
  await page.getByLabel('Available from').fill('2026-12-01');
  await page.locator('[data-save-profile]').click();
  await expect(page.getByText('Start with the usual steps of a recruitment')).toBeVisible();
  expect(calls.profiles).toEqual([expect.objectContaining({
    position: 'Java lead', skills: ['Java', 'Kafka'], address: '1 rue de Rivoli', city: 'Paris', targetDailyRate: 650,
    availabilityDate: '2026-12-01'
  })]);

  await page.getByRole('radio', { name: /Direct client/ }).check();
  await page.locator('[data-save-process]').click();
  await expect(page.getByRole('heading', { name: 'Your workspace is ready' })).toBeVisible();
  expect(calls.templates).toEqual([{ name: 'Direct client', steps: [
    { title: 'Discovery call', checklist: ['Daily rate stated', 'Start date confirmed'] },
    { title: 'Client interview', checklist: ['Stack and context reviewed', 'References ready'] },
    { title: 'Proposal', checklist: ['Rate, duration and start agreed'] }
  ] }]);

  await page.getByRole('button', { name: 'Add an opportunity' }).click();
  await expect(page).toHaveURL(/\/projects\/new$/);
  expect(calls.completed).toHaveLength(1);
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/dashboard$/);
});

test('lets a new account skip the setup for good (fr, phone)', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  const calls = await registerNewAccount(page, 'fr');

  await expect(page.getByRole('heading', { name: 'Bienvenue sur Indezy, Jean' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: test.info().outputPath('onboarding-phone.png') });
  // The dashboard leads back to the welcome steps until they are finished or skipped.
  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/welcome$/);

  await page.getByRole('button', { name: 'Passer la configuration' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  expect(calls.completed).toHaveLength(1);
  expect(calls.profiles).toHaveLength(0);
  await page.reload();
  await expect(page).toHaveURL(/\/dashboard$/);
});
