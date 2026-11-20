import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

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

  readonly articles = HELP_ARTICLES;
  article: HelpArticle | undefined;
  previous: HelpArticle | undefined;
  next: HelpArticle | undefined;
  headings: MarkdownHeading[] = [];

  ngOnInit(): void {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.article = helpArticle(params.get('slug'));
      const at = this.article ? HELP_ARTICLES.indexOf(this.article) : -1;
      this.previous = at > 0 ? HELP_ARTICLES[at - 1] : undefined;
      this.next = at >= 0 && at < HELP_ARTICLES.length - 1 ? HELP_ARTICLES[at + 1] : undefined;
      this.headings = [];
      this.changes.markForCheck();
      window.scrollTo?.({ top: 0 });
    });
  }

  showHeadings(headings: MarkdownHeading[]): void {
    this.headings = headings;
    this.changes.markForCheck();
  }

  /** Scrolls to a section of the article. */
  jump(event: Event, id: string): void {
    event.preventDefault();
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
