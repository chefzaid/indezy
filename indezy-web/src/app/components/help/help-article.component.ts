import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { useAddressLanguage } from './help-language';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';

import { AuthService } from '../../services/auth/auth.service';
import { MarkdownHeading } from '../../shared/components/markdown/markdown';
import { MarkdownViewComponent } from '../../shared/components/markdown/markdown-view.component';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';
import { HELP_ARTICLES, HelpArticle, helpArticle } from './help-articles';

/**
 * A help article (`/help/<slug>`): the articles listed on the left, the one read in the middle with its sections listed
 * beside it on a wide screen, the previous and next article at its foot.
 */
@Component({
  selector: 'app-help-article',
  imports: [RouterLink, RouterLinkActive, MatIconModule, TranslateModule, MarkdownViewComponent, PublicPageHeaderComponent],
  templateUrl: './help-article.component.html',
  styleUrls: ['./help-article.component.scss'],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HelpArticleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly translate = inject(TranslateService);

  /** The articles answered on this device, so Was this helpful? is asked once each. */
  private static readonly ANSWERED_KEY = 'indezy.help.answered';
  /** Whether the article read was answered: true or false once it was, null before. */
  answered: boolean | null = null;

  readonly articles = HELP_ARTICLES;
  article: HelpArticle | undefined;
  previous: HelpArticle | undefined;
  next: HelpArticle | undefined;
  headings: MarkdownHeading[] = [];

  ngOnInit(): void {
    useAddressLanguage(this.route, this.auth, this.translate);
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.article = helpArticle(params.get('slug'));
      const at = this.article ? HELP_ARTICLES.indexOf(this.article) : -1;
      this.previous = at > 0 ? HELP_ARTICLES[at - 1] : undefined;
      this.next = at >= 0 && at < HELP_ARTICLES.length - 1 ? HELP_ARTICLES[at + 1] : undefined;
      this.headings = [];
      this.answered = this.article && answeredOnThisDevice().includes(this.article.slug) ? true : null;
      this.changes.markForCheck();
      window.scrollTo?.({ top: 0 });
    });
  }

  showHeadings(headings: MarkdownHeading[]): void {
    this.headings = headings;
    this.changes.markForCheck();
  }

  /** Counts whether the article helped (nothing about who said it), once on this device. */
  answer(helpful: boolean): void {
    const slug = this.article?.slug;
    if (!slug || this.answered !== null) {
      return;
    }
    this.answered = helpful;
    try {
      localStorage.setItem(HelpArticleComponent.ANSWERED_KEY, JSON.stringify([...answeredOnThisDevice(), slug].slice(-100)));
    } catch {
      // Storage refused (private browsing): the question may come back.
    }
    this.http.post(`${environment.apiUrl}/public/help/${slug}/feedback`, { helpful }).subscribe({ error: () => undefined });
  }

  /** Scrolls to a section of the article. */
  jump(event: Event, id: string): void {
    event.preventDefault();
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}

function answeredOnThisDevice(): string[] {
  try {
    const stored = JSON.parse(localStorage.getItem('indezy.help.answered') ?? '[]');
    return Array.isArray(stored) ? stored.filter((slug): slug is string => typeof slug === 'string') : [];
  } catch {
    return [];
  }
}
