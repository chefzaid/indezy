import AxeBuilder from '@axe-core/playwright';
import { BrowserContext, Worker, chromium, expect, test } from '@playwright/test';
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
