import AxeBuilder from '@axe-core/playwright';
import { BrowserContext, Worker, chromium, expect, test } from '@playwright/test';
import http from 'node:http';
import { AddressInfo } from 'node:net';
import path from 'node:path';

const EXTENSION = path.resolve(__dirname, '../extension');
const BASE = 'https://indezy.test';

/** Chromium with the extension loaded, and its service worker. */
async function withExtension(): Promise<{ context: BrowserContext; worker: Worker }> {
  const executablePath = test.info().project.use.launchOptions?.executablePath;
  const context = await chromium.launchPersistentContext('', { headless: true, executablePath,
    args: [`--disable-extensions-except=${EXTENSION}`, `--load-extension=${EXTENSION}`] });
  const worker = context.serviceWorkers()[0] ?? await context.waitForEvent('serviceworker');
  // Indezy itself is not needed: the test checks which form the extension opens.
  await context.route(`${BASE}/**`, route => route.fulfill({ contentType: 'text/html', body: '<!doctype html><title>Indezy</title>' }));
  return { context, worker };
}

test('opens Indezy\'s form for the page or link chosen, at the address set in its options', async () => {
  const { context, worker } = await withExtension();
  try {
    const id = new URL(worker.url()).host;
    const options = await context.newPage();
    await options.goto(`chrome-extension://${id}/options.html`);
    const field = options.getByLabel('Your Indezy address');
    await expect(field).toHaveValue('https://indezy.swirlit.dev');
    await field.fill('http://indezy.test/app');
    await options.getByRole('button', { name: 'Save' }).click();
    await expect(options.getByRole('status')).toHaveText('Enter an https:// address without a path.');
    await expect(field).toHaveAttribute('aria-invalid', 'true');
    const audit = await new AxeBuilder({ page: options }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
    expect(audit.violations.map(violation => violation.id)).toEqual([]);
    await field.fill(`${BASE}/`);
    await options.getByRole('button', { name: 'Save' }).click();
    await expect(options.getByRole('status')).toHaveText('Saved.');
    await expect(field).toHaveValue(BASE);

    const opened = context.waitForEvent('page');
    await worker.evaluate(address => (globalThis as unknown as { openInIndezy: (url: string) => Promise<unknown> }).openInIndezy(address),
      'https://jobs.example/offer?id=7#apply');
    const form = await opened;
    await form.waitForURL(`${BASE}/projects/new?from=link&url=https%3A%2F%2Fjobs.example%2Foffer%3Fid%3D7%23apply`);

    // The browser's own pages are not offers: nothing opens.
    const pages = context.pages().length;
    expect(await worker.evaluate(() => (globalThis as unknown as { openInIndezy: (url: string) => Promise<unknown> })
      .openInIndezy('chrome://extensions'))).toBeNull();
    expect(context.pages()).toHaveLength(pages);
  } finally {
    await context.close();
  }
});

test('fills an application form with the Indezy profile read with its token, then opens Indezy\'s form for it', async () => {
  // A local Indezy answering the profile to its token, and a job board's application form.
  const form = `<!doctype html><html lang="en"><title>Apply</title><form>
    <label for="fn">First name</label><input id="fn" name="first_name">
    <label for="ln">Last name</label><input id="ln" name="last_name">
    <input type="email" name="contact" aria-label="Your email">
    <input name="telephone" placeholder="Téléphone">
    <label for="li">LinkedIn profile</label><input id="li" name="li">
    <label for="tjm">TJM souhaité</label><input id="tjm" type="number" name="tjm">
    <label for="city">City</label><input id="city" name="city" value="Paris">
    <label for="pw">Password</label><input id="pw" type="password" name="password">
    <label for="cv">Cover letter</label><textarea id="cv" name="message"></textarea>
    <button>Send</button></form></html>`;
  const server = http.createServer((request, response) => {
    if (request.url === '/api/application-profile') {
      const authorized = request.headers.authorization === 'Bearer idz_secret';
      response.writeHead(authorized ? 200 : 401, { 'Content-Type': 'application/json' });
      response.end(authorized ? JSON.stringify({ firstName: 'Léa', lastName: 'Martin', email: 'lea@example.test', phone: '06 12 34 56 78',
        city: 'Lyon', linkedin: 'https://www.linkedin.com/in/lea', targetDailyRate: 650 }) : '{}');
      return;
    }
    response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    response.end(request.url?.startsWith('/apply') ? form : '<!doctype html><title>Indezy</title>');
  });
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve));
  const local = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  const { context, worker } = await withExtension();
  try {
    await worker.evaluate(address => chrome.storage.sync.set({ base: address }), local);
    const board = await context.newPage();
    await board.goto(`${local}/apply`);
    const tab = await worker.evaluate(async () => (await chrome.tabs.query({ url: '*://127.0.0.1/apply*' }))[0]);
    type Fill = { fillForm: (tab: unknown) => Promise<number | null> };

    // Without a token, nothing is read and the page says how to set one.
    expect(await worker.evaluate(given => (globalThis as unknown as Fill).fillForm(given), tab)).toBeNull();
    await expect(board.getByRole('status')).toContainText('API token');
    await expect(board.locator('#fn')).toHaveValue('');

    await worker.evaluate(() => chrome.storage.local.set({ token: 'idz_secret' }));
    const opened = context.waitForEvent('page');
    expect(await worker.evaluate(given => (globalThis as unknown as Fill).fillForm(given), tab)).toBe(6);
    await expect(board.locator('#fn')).toHaveValue('Léa');
    await expect(board.locator('#ln')).toHaveValue('Martin');
    await expect(board.locator('[name=contact]')).toHaveValue('lea@example.test');
    await expect(board.locator('[name=telephone]')).toHaveValue('06 12 34 56 78');
    await expect(board.locator('#li')).toHaveValue('https://www.linkedin.com/in/lea');
    await expect(board.locator('#tjm')).toHaveValue('650');
    // What is typed already, a password and the cover letter are left as they are.
    await expect(board.locator('#city')).toHaveValue('Paris');
    await expect(board.locator('#pw')).toHaveValue('');
    await expect(board.locator('#cv')).toHaveValue('');
    await expect(board.getByRole('status')).toContainText('6 fields filled');
    const indezy = await opened;
    await indezy.waitForURL(`${local}/projects/new?from=link&url=${encodeURIComponent(`${local}/apply`)}`);

    await worker.evaluate(() => chrome.storage.local.set({ token: 'idz_wrong' }));
    expect(await worker.evaluate(given => (globalThis as unknown as Fill).fillForm(given), tab)).toBeNull();
  } finally {
    await context.close();
    server.close();
  }
});
