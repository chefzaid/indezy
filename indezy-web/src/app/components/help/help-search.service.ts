import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';
import { plainText } from '../../shared/components/markdown/markdown';
import { articleLanguage } from '../../shared/components/markdown/markdown-view.component';
import { HELP_ARTICLES, HelpArticle } from './help-articles';

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

/** The search over the help articles, in the help center and the help panel: their titles, summaries and text. */
@Injectable({ providedIn: 'root' })
export class HelpSearchService {
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);
  private readonly texts: Record<string, Record<string, string>> = {};

  /** The articles whose title, summary or text holds what was typed, those matching by title first. */
  search(typed: string): Observable<HelpHit[]> {
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
