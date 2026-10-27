// Writes the presentation page as static HTML after the build (see docs/features.md#presentation-page), so that search
// engines and link previews read it without running the app: home-fr.html for / and home-en.html for /en, served by
// nginx. Each is the built index.html with the page's title, description, card, languages and data in its head, and
// the page's text in <app-root>, which the app replaces once it starts.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const SITE = (process.env.INDEZY_PUBLIC_URL ?? 'https://indezy.swirlit.dev').replace(/\/$/, '');
const PATHS = { fr: '/', en: '/en' };

export function escape(text) {
  return String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/** The head tags of the page in {@code language}, each marked so the app can replace them. */
export function head(home, language, site = SITE) {
  const url = site + PATHS[language];
  const image = `${site}/assets/brand/og-image.png`;
  const data = { '@context': 'https://schema.org', '@type': 'SoftwareApplication', name: 'Indezy', url, description: home.meta.description,
    applicationCategory: 'BusinessApplication', operatingSystem: 'Web', inLanguage: ['fr', 'en'], image };
  const meta = (attribute, name, content) => `<meta ${attribute}="${name}" content="${escape(content)}" data-prerendered>`;
  return [
    meta('name', 'description', home.meta.description),
    meta('property', 'og:type', 'website'),
    meta('property', 'og:title', home.meta.title),
    meta('property', 'og:description', home.meta.description),
    meta('property', 'og:url', url),
    meta('property', 'og:image', image),
    meta('property', 'og:locale', language === 'en' ? 'en_GB' : 'fr_FR'),
    meta('name', 'twitter:card', 'summary_large_image'),
    `<link rel="canonical" href="${url}" data-prerendered>`,
    `<link rel="alternate" hreflang="fr" href="${site}/" data-prerendered>`,
    `<link rel="alternate" hreflang="en" href="${site}/en" data-prerendered>`,
    `<link rel="alternate" hreflang="x-default" href="${site}/" data-prerendered>`,
    `<script type="application/ld+json" data-prerendered>${JSON.stringify(data).replace(/</g, '\\u003c')}</script>`
  ].join('\n  ');
}

/** The page's text, in the order the app shows it. */
export function body(home) {
  const section = (title, items) => `<section><h2>${escape(title)}</h2>${items}</section>`;
  const features = ['feed', 'pipeline', 'network', 'reports', 'invoices', 'dashboard']
    .map(key => `<article><h3>${escape(home.features[key].title)}</h3><p>${escape(home.features[key].text)}</p></article>`).join('');
  const steps = ['profile', 'network', 'opportunities', 'pipeline', 'reports']
    .map(key => `<li><h3>${escape(home.steps[key].title)}</h3><p>${escape(home.steps[key].text)}</p></li>`).join('');
  const trust = ['yours', 'export', 'links']
    .map(key => `<li><h3>${escape(home.trust[key].title)}</h3><p>${escape(home.trust[key].text)}</p></li>`).join('');
  const faq = ['who', 'sites', 'data', 'languages']
    .map(key => `<h3>${escape(home.faq[key].q)}</h3><p>${escape(home.faq[key].a)}</p>`).join('');
  return `<main class="prerendered-home"><p>${escape(home.hero.eyebrow)}</p><h1>${escape(home.hero.title)}</h1>` +
    `<p>${escape(home.hero.lead)}</p>` +
    section(home.features.title, features) + section(home.steps.title, `<ol>${steps}</ol>`) +
    section(home.trust.title, `<ul>${trust}</ul>`) + section(home.faq.title, faq) + `<footer>${escape(home.footer)}</footer></main>`;
}

/** The built index.html made into the page in {@code language}. */
export function page(index, home, language, site = SITE) {
  return index
    .replace(/<html lang="[^"]*">/, `<html lang="${language}">`)
    .replace(/<title>[^<]*<\/title>/, `<title>${escape(home.meta.title)}</title>`)
    .replace(/\s*<meta name="description"[^>]*>/, '')
    .replace('</head>', `  ${head(home, language, site)}\n</head>`)
    .replace(/<app-root>\s*<\/app-root>/, `<app-root>${body(home)}</app-root>`);
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const dist = process.argv[2] ?? 'dist/indezy-web/browser';
  const index = readFileSync(join(dist, 'index.html'), 'utf8');
  for (const language of ['fr', 'en']) {
    const home = JSON.parse(readFileSync(join(dist, 'assets', 'i18n', `${language}.json`), 'utf8')).home;
    writeFileSync(join(dist, `home-${language}.html`), page(index, home, language));
  }
  console.log(`Presentation page written to ${dist}/home-fr.html and home-en.html`);
}
