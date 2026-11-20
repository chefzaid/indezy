import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ViewEncapsulation, DestroyRef, ElementRef, EventEmitter, HostListener, Input, OnChanges, OnInit,
  Output, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { MarkdownHeading, renderMarkdown } from './markdown';

/** Which language an article is read in: the interface's, French by default. */
export function articleLanguage(translate: TranslateService): 'fr' | 'en' {
  return (translate.getCurrentLang?.() ?? translate.currentLang) === 'en' ? 'en' : 'fr';
}

/**
 * An article written in Markdown (`assets/<folder>/<language>/<name>.md`, the app's own files, never members' text), in
 * the interface's language and again when it changes; its links within the app followed without reloading the page.
 */
@Component({
  selector: 'app-markdown-view',
  imports: [TranslateModule],
  template: `
    @if (failed) {
      <p class="missing" role="alert" data-markdown-missing>{{ 'help.loadError' | translate }}</p>
    } @else {
      <div class="markdown" [innerHTML]="html" data-markdown></div>
    }
  `,
  styleUrls: ['./markdown-view.component.scss'],
  // The article's HTML is not part of the template: its styles reach it unscoped, under .markdown alone.
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.Eager
})
export class MarkdownViewComponent implements OnInit, OnChanges {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly destroyRef = inject(DestroyRef);

  /** The folder under assets: `help` or `legal`. */
  @Input({ required: true }) folder!: string;
  @Input({ required: true }) name!: string;
  @Output() readonly headings = new EventEmitter<MarkdownHeading[]>();
  html: SafeHtml = '';
  failed = false;

  ngOnInit(): void {
    this.translate.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  ngOnChanges(): void {
    this.load();
  }

  private load(): void {
    const path = `assets/${this.folder}/${articleLanguage(this.translate)}/${this.name}.md`;
    this.http.get(path, { responseType: 'text' }).subscribe({
      next: source => {
        const rendered = renderMarkdown(source);
        // The articles are the app's own files (assets/), never a member's text: their headings keep their ids.
        this.html = this.sanitizer.bypassSecurityTrustHtml(rendered.html);
        this.failed = false;
        this.headings.emit(rendered.headings);
        this.changes.markForCheck();
      },
      error: () => {
        this.failed = true;
        this.headings.emit([]);
        this.changes.markForCheck();
      }
    });
  }

  /** A link to a page of the app opens it in place; one to another site opens in a new tab. */
  @HostListener('click', ['$event'])
  follow(event: MouseEvent): void {
    const link = (event.target as HTMLElement).closest('a');
    if (!link || !this.host.nativeElement.contains(link) || event.ctrlKey || event.metaKey || event.shiftKey) {
      return;
    }
    const href = link.getAttribute('href') ?? '';
    if (href.startsWith('/')) {
      event.preventDefault();
      void this.router.navigateByUrl(href);
    } else if (/^https?:/.test(href)) {
      link.setAttribute('target', '_blank');
      link.setAttribute('rel', 'noopener noreferrer');
    }
  }
}
