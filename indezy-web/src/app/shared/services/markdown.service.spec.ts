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
});
