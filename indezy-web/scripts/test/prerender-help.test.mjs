import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { markdown, page, sitemap, slugsOf } from '../prerender-help.mjs';

const index = readFileSync(new URL('../../src/index.html', import.meta.url), 'utf8');
const source = readFileSync(new URL('../../src/app/components/help/help-articles.ts', import.meta.url), 'utf8');

test('reads every article the help center lists', () => {
  const slugs = slugsOf(source);
  assert.ok(slugs.includes('getting-started') && slugs.includes('invoices-quotes') && slugs.includes('whats-new'));
  assert.equal(new Set(slugs).size, slugs.length);
});

test('writes an article as plain HTML, its links kept in its language and its images left out', () => {
  const html = markdown('Intro **bold** and `code`.\n\n## Steps\n\n1. Open [Offers](/help/offers)\n2. Then <b>x</b>\n\n' +
    '![A screen](/assets/help/screens/x.jpg)\n\n- one\n- two\n\n| Key | What |\n| --- | --- |\n| `J` | Next |\n\n> **Tip:** see [Help](/help)', 'fr');
  assert.match(html, /<p>Intro <strong>bold<\/strong> and <code>code<\/code>.<\/p>/);
  assert.match(html, /<h2>Steps<\/h2>/);
  assert.match(html, /<ol><li>Open <a href="\/aide\/offers">Offers<\/a><\/li><li>Then &lt;b&gt;x&lt;\/b&gt;<\/li><\/ol>/);
  assert.doesNotMatch(html, /img|screens/);
  assert.match(html, /<ul><li>one<\/li><li>two<\/li><\/ul>/);
  assert.match(html, /<table><tr><td>Key<\/td><td>What<\/td><\/tr><tr><td><code>J<\/code><\/td><td>Next<\/td><\/tr><\/table>/);
  assert.match(html, /<blockquote><p><strong>Tip:<\/strong> see <a href="\/aide">Help<\/a><\/p><\/blockquote>/);
  assert.match(markdown('[Offers](/help/offers)', 'en'), /href="\/en\/help\/offers"/);
});

test('makes the built page into the article, at its address in each language', () => {
  const html = page(index, { title: 'Le fil d\'offres · Aide', description: 'Les offres "freelance"', body: '<h1>Le fil</h1>', slug: 'offers' },
    'fr', 'https://indezy.example');
  assert.match(html, /<html lang="fr">/);
  assert.match(html, /<title>Le fil d'offres · Aide<\/title>/);
  assert.equal(html.match(/<meta name="description"/g).length, 1);
  assert.match(html, /content="Les offres &quot;freelance&quot;"/);
  assert.match(html, /<link rel="canonical" href="https:\/\/indezy.example\/aide\/offers" data-prerendered>/);
  assert.match(html, /hreflang="en" href="https:\/\/indezy.example\/en\/help\/offers"/);
  assert.match(html, /<app-root><main class="prerendered-help"><h1>Le fil<\/h1><\/main><\/app-root>/);
});

test('lists the presentation page and every help page in the sitemap, with their languages', () => {
  const xml = sitemap(['offers'], 'https://indezy.example');
  for (const loc of ['/', '/en', '/aide', '/en/help', '/aide/offers', '/en/help/offers']) {
    assert.match(xml, new RegExp(`<loc>https://indezy.example${loc.replace(/\//g, '\\/')}</loc>`));
  }
  assert.equal(xml.match(/<url>/g).length, 6);
  assert.match(xml, /hreflang="en" href="https:\/\/indezy.example\/en\/help\/offers"/);
});
