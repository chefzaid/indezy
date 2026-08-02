import { Injectable } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';

/**
 * Renders a small, safe subset of Markdown (headings, bold, italic, inline code,
 * http(s) links and unordered lists). Input is HTML-escaped first, so user content
 * can never inject markup — only the whitelisted tags this service emits are produced.
 */
@Injectable({ providedIn: 'root' })
export class MarkdownService {
  constructor(private readonly sanitizer: DomSanitizer) {}

  /** Renders Markdown to sanitized, bindable HTML. */
  render(text: string): SafeHtml {
    return this.sanitizer.bypassSecurityTrustHtml(this.toHtml(text ?? ''));
  }

  /** Converts the supported Markdown subset to an HTML string. */
  toHtml(text: string): string {
    const lines = (text ?? '').split(/\r?\n/);
    const blocks: string[] = [];

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (line.trim() === '') {
        continue;
      }

      const heading = /^(#{1,3})\s+(.*)$/.exec(line);
      if (heading) {
        const level = heading[1].length;
        blocks.push(`<h${level}>${this.inline(heading[2])}</h${level}>`);
        continue;
      }

      if (/^\s*[-*]\s+/.test(line)) {
        const items: string[] = [];
        while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
          items.push(`<li>${this.inline(lines[i].replace(/^\s*[-*]\s+/, ''))}</li>`);
          i++;
        }
        i--;
        blocks.push(`<ul>${items.join('')}</ul>`);
        continue;
      }

      const paragraph: string[] = [];
      while (i < lines.length && lines[i].trim() !== ''
          && !/^(#{1,3})\s+/.test(lines[i]) && !/^\s*[-*]\s+/.test(lines[i])) {
        paragraph.push(this.inline(lines[i]));
        i++;
      }
      i--;
      blocks.push(`<p>${paragraph.join('<br>')}</p>`);
    }

    return blocks.join('');
  }

  /** Escapes HTML then applies inline Markdown (code, bold, italic, links). */
  private inline(text: string): string {
    let html = this.escape(text);
    html = html.replace(/`([^`]+?)`/g, '<code>$1</code>');
    html = html.replace(/\*\*([^*]+?)\*\*/g, '<strong>$1</strong>');
    html = html.replace(/(^|[^*])\*([^*]+?)\*(?!\*)/g, '$1<em>$2</em>');
    html = html.replace(
      /\[([^\]]+?)\]\((https?:\/\/[^\s)]+)\)/g,
      '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
    );
    return html;
  }

  private escape(text: string): string {
    return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }
}
