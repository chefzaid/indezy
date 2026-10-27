import { strict as assert } from 'node:assert';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { page } from '../prerender-home.mjs';

const index = readFileSync(new URL('../../src/index.html', import.meta.url), 'utf8');
const homeOf = language => JSON.parse(readFileSync(new URL(`../../src/assets/i18n/${language}.json`, import.meta.url), 'utf8')).home;

test('writes the French page with its text, title, description, card and languages', () => {
  const html = page(index, homeOf('fr'), 'fr', 'https://indezy.example');
  assert.match(html, /<html lang="fr">/);
  assert.match(html, /<title>Indezy · Trouvez la mission/);
  assert.equal(html.match(/<meta name="description"/g).length, 1);
  assert.match(html, /<h1>Trouvez la mission, décrochez-la au bon TJM, puis menez-la.<\/h1>/);
  assert.match(html, /<h3>À qui s'adresse Indezy \?<\/h3>/);
  assert.match(html, /<link rel="canonical" href="https:\/\/indezy.example\/" data-prerendered>/);
  assert.match(html, /hreflang="en" href="https:\/\/indezy.example\/en"/);
  assert.match(html, /property="og:image" content="https:\/\/indezy.example\/assets\/brand\/og-image.png"/);
  const data = JSON.parse(html.match(/<script type="application\/ld\+json" data-prerendered>(.*)<\/script>/)[1]);
  assert.equal(data['@type'], 'SoftwareApplication');
  assert.equal(data.url, 'https://indezy.example/');
});

test('writes the English page at /en, escaping what it writes', () => {
  const home = { ...homeOf('en'), hero: { ...homeOf('en').hero, title: 'Rates <script>alert(1)</script> & "quotes"' } };
  const html = page(index, home, 'en', 'https://indezy.example');
  assert.match(html, /<html lang="en">/);
  assert.match(html, /<link rel="canonical" href="https:\/\/indezy.example\/en"/);
  assert.match(html, /<h1>Rates &lt;script&gt;alert\(1\)&lt;\/script&gt; &amp; &quot;quotes&quot;<\/h1>/);
  assert.match(html, /og:locale" content="en_GB"/);
});
