import { SecurityContext } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { MarkdownService } from './markdown.service';

describe('MarkdownService', () => {
  let service: MarkdownService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(MarkdownService);
  });

  it('renders headings, bold, italic and inline code', () => {
    expect(service.toHtml('# Title')).toBe('<h1>Title</h1>');
    expect(service.toHtml('#\u00a0Title')).toBe('<h1>Title</h1>');
    expect(service.toHtml('**bold**')).toBe('<p><strong>bold</strong></p>');
    expect(service.toHtml('an *italic* word')).toBe('<p>an <em>italic</em> word</p>');
    expect(service.toHtml('use `code` here')).toBe('<p>use <code>code</code> here</p>');
  });

  it('renders unordered lists', () => {
    expect(service.toHtml('- one\n- two')).toBe('<ul><li>one</li><li>two</li></ul>');
  });

  it('renders http(s) links and ignores other schemes', () => {
    expect(service.toHtml('[site](https://x.com)'))
      .toBe('<p><a href="https://x.com" target="_blank" rel="noopener noreferrer">site</a></p>');
    // A javascript: URL does not match the link rule and stays inert (escaped) text.
    expect(service.toHtml('[x](javascript:alert(1))'))
      .toBe('<p>[x](javascript:alert(1))</p>');
  });

  it('escapes HTML so raw markup cannot be injected', () => {
    expect(service.toHtml('<script>alert(1)</script>'))
      .toBe('<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>');
  });

  it('joins consecutive lines of a paragraph with line breaks', () => {
    expect(service.toHtml('line one\nline two')).toBe('<p>line one<br>line two</p>');
  });
  it('keeps Angular sanitization enabled for rendered Markdown', () => {
    const rendered = service.render('[site](https://example.com) <img src=x onerror=alert(1)>');
    expect(typeof rendered).toBe('string');
    const sanitized = TestBed.inject(DomSanitizer).sanitize(SecurityContext.HTML, rendered);
    const container = document.createElement('div');
    container.innerHTML = sanitized ?? '';
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('a')?.getAttribute('href')).toBe('https://example.com');
  });

  it('handles long malformed link and heading input without repeated backtracking', () => {
    const brackets = '['.repeat(100_000);
    expect(service.toHtml(brackets)).toBe(`<p>${brackets}</p>`);
    expect(service.toHtml('#' + ' '.repeat(100_000))).toBe('<h1></h1>');
  });

});
