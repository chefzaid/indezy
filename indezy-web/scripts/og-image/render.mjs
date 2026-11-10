// Renders og-image.html into src/assets/brand/og-image.png, the 1200 × 630 card link previews show (see
// docs/features.md#presentation-page). Run from indezy-web after a theme change: node scripts/og-image/render.mjs.
// PLAYWRIGHT_CHROMIUM points at another Chromium when Playwright's own is not installed.
import { chromium } from 'playwright';
import { fileURLToPath } from 'node:url';

const html = fileURLToPath(new URL('./og-image.html', import.meta.url));
const output = fileURLToPath(new URL('../../src/assets/brand/og-image.png', import.meta.url));
const browser = await chromium.launch(process.env.PLAYWRIGHT_CHROMIUM ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM } : {});
const page = await browser.newPage({ viewport: { width: 1200, height: 630 } });
await page.goto(`file://${html}`);
await page.evaluate(() => document.fonts.ready);
await page.screenshot({ path: output });
await browser.close();
