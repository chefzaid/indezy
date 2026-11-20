import { Marked, Tokens } from 'marked';

/** A heading of a rendered article, for its table of contents. */
export interface MarkdownHeading {
  id: string;
  text: string;
}

/** An id for a heading: lower-case words joined by dashes, accents dropped. */
export function headingId(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

/** The text of rendered HTML as read: its entities (apostrophes, ampersands, quotes) turned back into characters. */
function decodeEntities(text: string): string {
  return text.replace(/&#39;/g, '\'').replace(/&quot;/g, '"').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
}

/** Markdown as HTML, its second-level headings given ids (and listed), its images loaded lazily. */
export function renderMarkdown(source: string): { html: string; headings: MarkdownHeading[] } {
  const headings: MarkdownHeading[] = [];
  const marked = new Marked({
    gfm: true,
    renderer: {
      heading({ tokens, depth }: Tokens.Heading): string {
        const text = this.parser.parseInline(tokens);
        const plain = decodeEntities(text.replace(/<[^>]+>/g, ''));
        if (depth !== 2) {
          return `<h${depth}>${text}</h${depth}>`;
        }
        const id = headingId(plain);
        headings.push({ id, text: plain });
        return `<h2 id="${id}">${text}</h2>`;
      },
      image({ href, text }: Tokens.Image): string {
        return `<figure><img src="${href}" alt="${text.replace(/"/g, '&quot;')}" loading="lazy"><figcaption>${text}</figcaption></figure>`;
      }
    }
  });
  return { html: marked.parse(source, { async: false }), headings };
}

/** The text of an article without its Markdown, for searching it. */
export function plainText(source: string): string {
  return source.replace(/!\[[^\]]*\]\([^)]*\)/g, ' ').replace(/\[([^\]]*)\]\([^)]*\)/g, '$1').replace(/[#>*_`|-]+/g, ' ')
    .replace(/\s+/g, ' ').trim();
}
