import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, catchError, debounceTime, distinctUntilChanged, forkJoin, map, of, switchMap } from 'rxjs';

import { AuthService } from '../../services/auth/auth.service';
import { PageHeaderComponent } from '../../shared/components/page-header/page-header.component';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';
import { plainText } from '../../shared/components/markdown/markdown';
import { articleLanguage } from '../../shared/components/markdown/markdown-view.component';
import { HELP_ARTICLES, HELP_SECTIONS, HelpArticle, HelpSection } from './help-articles';

/** An article found by a search, with the words around the first match. */
export interface HelpHit {
  article: HelpArticle;
  excerpt: string | null;
}

/** The words around the first place the text holds the query, accents and case aside; none when it does not. */
export function excerptAround(text: string, query: string, room = 90): string | null {
  const fold = (value: string): string => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
  const at = fold(text).indexOf(fold(query));
  if (at < 0) {
    return null;
  }
  const start = Math.max(0, at - room);
  const end = Math.min(text.length, at + query.length + room);
  return (start > 0 ? '…' : '') + text.slice(start, end).trim() + (end < text.length ? '…' : '');
}

/**
 * The help center (`/help`, open without an account): how Indezy works, area by area, each article with screenshots; a
 * search over their titles, summaries and text; and the legal pages.
 */
@Component({
  selector: 'app-help-center',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule, PageHeaderComponent,
    PublicPageHeaderComponent],
  templateUrl: './help-center.component.html',
  styleUrls: ['./help-center.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HelpCenterComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly sections = HELP_SECTIONS;
  readonly query = new FormControl('', { nonNullable: true });
  /** The articles found while a search is typed (two characters or more); none otherwise. */
  hits: HelpHit[] | null = null;
  private texts: Record<string, Record<string, string>> = {};

  articles(section: HelpSection): HelpArticle[] {
    return HELP_ARTICLES.filter(article => article.section === section);
  }

  ngOnInit(): void {
    this.query.valueChanges.pipe(debounceTime(200), map(typed => typed.trim()), distinctUntilChanged(),
      switchMap(typed => typed.length < 2 ? of(null) : this.search(typed)), takeUntilDestroyed(this.destroyRef))
      .subscribe(hits => {
        this.hits = hits;
        this.changes.markForCheck();
      });
  }

  /** The articles whose title, summary or text holds what was typed, those matching by title first. */
  private search(typed: string): Observable<HelpHit[]> {
    return this.load().pipe(map(texts => {
      const byTitle: HelpHit[] = [];
      const byText: HelpHit[] = [];
      for (const article of HELP_ARTICLES) {
        const title = this.translate.instant(`help.articles.${article.slug}.title`) + ' ' +
          this.translate.instant(`help.articles.${article.slug}.summary`);
        const excerpt = excerptAround(texts[article.slug] ?? '', typed);
        if (excerptAround(title, typed) !== null) {
          byTitle.push({ article, excerpt });
        } else if (excerpt !== null) {
          byText.push({ article, excerpt });
        }
      }
      return [...byTitle, ...byText];
    }));
  }

  /** Every article's text in the reading language, read once. */
  private load(): Observable<Record<string, string>> {
    const language = articleLanguage(this.translate);
    if (this.texts[language]) {
      return of(this.texts[language]);
    }
    return forkJoin(HELP_ARTICLES.map(article => this.http.get(`assets/help/${language}/${article.slug}.md`, { responseType: 'text' })
      .pipe(map(source => [article.slug, plainText(source)] as const), catchError(() => of([article.slug, ''] as const)))))
      .pipe(map(entries => {
        this.texts[language] = Object.fromEntries(entries);
        return this.texts[language];
      }));
  }
}
