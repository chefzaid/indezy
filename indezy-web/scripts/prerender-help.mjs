// Writes the help center as static HTML after the build (see docs/features.md#help-center-and-legal-pages), so search
// engines read it without running the app: /aide and /aide/<slug> in French, /en/help and /en/help/<slug> in English,
// each the built index.html with the article's title, description, canonical address and languages in its head and its
// text in <app-root>, which the app replaces once it starts; and sitemap.xml with the presentation and help pages.
import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { SITE, escape } from './prerender-home.mjs';

/** Where the help is in each language. */
export const HELP_PATHS = { fr: '/aide', en: '/en/help' };

/** The articles' slugs, in reading order, as help-articles.ts lists them. */
export function slugsOf(source) {
  return [...source.matchAll(/slug: '([a-z0-9-]+)'/g)].map(match => match[1]);
}

/** A line's inline Markdown as HTML: links (to other articles in the same language), bold, italic and code. */
function inline(text, language) {
  return escape(text)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|\W)_([^_]+)_(?=\W|$)/g, '$1<em>$2</em>')
    .replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (whole, label, href) => {
      const article = /^\/help\/([a-z0-9-]+)$/.exec(href);
      const target = article ? `${HELP_PATHS[language]}/${article[1]}` : href === '/help' ? HELP_PATHS[language] : href;
      return /^(\/|https:\/\/)/.test(target) ? `<a href="${target}">${label}</a>` : label;
    });
}

/** The article's Markdown as plain HTML: headings, paragraphs, lists, quotes and tables; images left out. */
export function markdown(source, language) {
  const html = [];
  let list = null;
  let paragraph = [];
  let table = [];
  const flush = () => {
    if (paragraph.length) {
      html.push(`<p>${inline(paragraph.join(' '), language)}</p>`);
      paragraph = [];
    }
    if (list) {
      html.push(`<${list.tag}>${list.items.map(item => `<li>${inline(item, language)}</li>`).join('')}</${list.tag}>`);
      list = null;
    }
    if (table.length) {
      const rows = table.filter(row => !/^\|?\s*:?-{3,}/.test(row))
        .map(row => row.replace(/^\||\|$/g, '').split('|').map(cell => `<td>${inline(cell.trim(), language)}</td>`).join(''));
      html.push(`<table>${rows.map(row => `<tr>${row}</tr>`).join('')}</table>`);
      table = [];
    }
  };
  for (const raw of source.split(/\r?\n/)) {
    const line = raw.trimEnd();
    const heading = /^(#{1,4})\s+(.*)$/.exec(line);
    const bullet = /^\s*[-*]\s+(.*)$/.exec(line);
    const numbered = /^\s*\d+\.\s+(.*)$/.exec(line);
    if (!line.trim() || /^!\[/.test(line.trim())) {
      flush();
    } else if (heading) {
      flush();
      const level = Math.min(Math.max(heading[1].length, 2), 4);
      html.push(`<h${level}>${inline(heading[2], language)}</h${level}>`);
    } else if (line.startsWith('|')) {
      if (paragraph.length || list) {
        flush();
      }
      table.push(line);
    } else if (bullet || numbered) {
      const tag = bullet ? 'ul' : 'ol';
      if (paragraph.length || table.length || (list && list.tag !== tag)) {
        flush();
      }
      list ??= { tag, items: [] };
      list.items.push((bullet ?? numbered)[1]);
    } else if (line.startsWith('>')) {
      flush();
      html.push(`<blockquote><p>${inline(line.replace(/^>\s?/, ''), language)}</p></blockquote>`);
    } else if (list) {
      list.items[list.items.length - 1] += ' ' + line.trim();
    } else {
      paragraph.push(line.trim());
    }
  }
  flush();
  return html.join('\n');
}

/** The head tags of a help page: its description, canonical address and its two languages. */
export function head(description, slug, language, site = SITE) {
  const path = suffix => `${site}${HELP_PATHS[suffix]}${slug ? '/' + slug : ''}`;
  return [
    `<meta name="description" content="${escape(description)}" data-prerendered>`,
    `<link rel="canonical" href="${path(language)}" data-prerendered>`,
    `<link rel="alternate" hreflang="fr" href="${path('fr')}" data-prerendered>`,
    `<link rel="alternate" hreflang="en" href="${path('en')}" data-prerendered>`,
    `<link rel="alternate" hreflang="x-default" href="${path('fr')}" data-prerendered>`
  ].join('\n  ');
}

/** The built index.html made into a help page. */
export function page(index, { title, description, body, slug }, language, site = SITE) {
  return index
    .replace(/<html lang="[^"]*">/, `<html lang="${language}">`)
    .replace(/<title>[^<]*<\/title>/, `<title>${escape(title)}</title>`)
    .replace(/\s*<meta name="description"[^>]*>/, '')
    .replace('</head>', `  ${head(description, slug, language, site)}\n</head>`)
    .replace(/<app-root>\s*<\/app-root>/, `<app-root><main class="prerendered-help">${body}</main></app-root>`);
}

/** The help center's own page: its title and every article with its summary. */
export function centerBody(help, slugs, language) {
  const items = slugs.map(slug => `<li><a href="${HELP_PATHS[language]}/${slug}">${escape(help.articles[slug].title)}</a>: ` +
    `${escape(help.articles[slug].summary)}</li>`).join('');
  return `<h1>${escape(help.title)}</h1><ul>${items}</ul>`;
}

/** sitemap.xml: the presentation page and the help center, each address with its two languages. */
export function sitemap(slugs, site = SITE) {
  const entry = (fr, en) => [fr, en].map(loc => `  <url>\n    <loc>${site}${loc}</loc>\n` +
    `    <xhtml:link rel="alternate" hreflang="fr" href="${site}${fr}"/>\n` +
    `    <xhtml:link rel="alternate" hreflang="en" href="${site}${en}"/>\n` +
    `    <xhtml:link rel="alternate" hreflang="x-default" href="${site}${fr}"/>\n  </url>`).join('\n');
  const entries = [entry('/', '/en'), entry(HELP_PATHS.fr, HELP_PATHS.en),
    ...slugs.map(slug => entry(`${HELP_PATHS.fr}/${slug}`, `${HELP_PATHS.en}/${slug}`))];
  return '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" ' +
    `xmlns:xhtml="http://www.w3.org/1999/xhtml">\n${entries.join('\n')}\n</urlset>\n`;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const dist = process.argv[2] ?? 'dist/indezy-web/browser';
  const index = readFileSync(join(dist, 'index.html'), 'utf8');
  const slugs = slugsOf(readFileSync('src/app/components/help/help-articles.ts', 'utf8'));
  for (const language of ['fr', 'en']) {
    const help = JSON.parse(readFileSync(join(dist, 'assets', 'i18n', `${language}.json`), 'utf8')).help;
    writeFileSync(join(dist, `help-${language}.html`), page(index, { title: `${help.title} · Indezy`, description: help.subtitle ?? help.title,
      body: centerBody(help, slugs, language), slug: null }, language));
    for (const slug of slugs) {
      const article = help.articles[slug];
      const text = readFileSync(join(dist, 'assets', 'help', language, `${slug}.md`), 'utf8');
      writeFileSync(join(dist, `help-${language}-${slug}.html`), page(index, { title: `${article.title} · ${help.title} · Indezy`,
        description: article.summary, body: `<h1>${escape(article.title)}</h1>\n${markdown(text, language)}`, slug }, language));
    }
  }
  writeFileSync(join(dist, 'sitemap.xml'), sitemap(slugs));
  console.log(`Help center written to ${dist}/help-<fr|en>[-<slug>].html (${slugs.length} articles) and sitemap.xml`);
}
