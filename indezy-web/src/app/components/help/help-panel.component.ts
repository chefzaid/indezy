import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged, map, of, switchMap } from 'rxjs';
import { MarkdownViewComponent } from '../../shared/components/markdown/markdown-view.component';
import { HELP_ARTICLES, HelpArticle } from './help-articles';
import { HelpHit, HelpSearchService } from './help-search.service';

/** The help article of each page of the app, by the first segments of its address; the getting started one elsewhere. */
const ARTICLE_BY_PATH: readonly [RegExp, string][] = [
  [/^\/dashboard/, 'dashboard'],
  [/^\/(projects|kanban|archive|timeline)/, 'pipeline'],
  [/^\/offers/, 'offers'],
  [/^\/(clients|contacts)/, 'clients-contacts'],
  [/^\/calendar/, 'calendar'],
  [/^\/activity-reports/, 'activity-reports'],
  [/^\/(invoices|quotes|expenses)/, 'invoices-quotes'],
  [/^\/documents/, 'documents'],
  [/^\/network/, 'network'],
  [/^\/(profile|welcome)/, 'account']
];

/** The help article that explains the page at {@code url}. */
export function helpArticleFor(url: string): HelpArticle {
  const path = url.split(/[?#]/)[0];
  const slug = ARTICLE_BY_PATH.find(([pattern]) => pattern.test(path))?.[1] ?? 'getting-started';
  return HELP_ARTICLES.find(article => article.slug === slug) ?? HELP_ARTICLES[0];
}

/**
 * The help of the page in use, in a panel beside it (see docs/features.md#help-center-and-legal-pages): its article, a
 * search over all the help whose results open in the panel, links to the full article and the whole help center, and
 * the ways to support: report a problem, suggest a feature, and the requests already sent.
 */
@Component({
  selector: 'app-help-panel',
  imports: [ReactiveFormsModule, RouterLink, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule,
    TranslateModule, MarkdownViewComponent],
  template: `
    <div class="head">
      <h2 mat-dialog-title><mat-icon aria-hidden="true">{{ article.icon }}</mat-icon>{{ 'help.articles.' + article.slug + '.title' | translate }}</h2>
      <button mat-icon-button type="button" mat-dialog-close [aria-label]="'common.close' | translate"><mat-icon>close</mat-icon></button>
    </div>
    <mat-dialog-content class="content" data-help-panel>
      <mat-form-field appearance="outline" class="search" subscriptSizing="dynamic">
        <mat-icon matPrefix aria-hidden="true">search</mat-icon>
        <mat-label>{{ 'help.panel.search' | translate }}</mat-label>
        <input matInput type="search" [formControl]="query" maxlength="80" data-help-panel-search>
      </mat-form-field>
      @if (hits !== null) {
        <ul class="hits" data-help-panel-hits>
          @for (hit of hits; track hit.article.slug) {
            <li>
              <button type="button" class="hit" (click)="show(hit.article)" [attr.data-help-panel-hit]="hit.article.slug">
                <strong>{{ 'help.articles.' + hit.article.slug + '.title' | translate }}</strong>
                @if (hit.excerpt) {
                  <span>{{ hit.excerpt }}</span>
                }
              </button>
            </li>
          } @empty {
            <li data-help-panel-no-hit>{{ 'help.noResult' | translate }}</li>
          }
        </ul>
      } @else {
        <app-markdown-view folder="help" [name]="article.slug"></app-markdown-view>
      }
    </mat-dialog-content>
    <mat-dialog-actions class="actions">
      <a mat-stroked-button [routerLink]="['/help', article.slug]" (click)="close()" data-help-panel-full>
        <mat-icon>open_in_full</mat-icon>{{ 'help.panel.full' | translate }}
      </a>
      <a mat-button routerLink="/help" (click)="close()">{{ 'help.panel.all' | translate }}</a>
      <a mat-button routerLink="/support" [queryParams]="{ kind: 'PROBLEM', page: page }" (click)="close()" data-help-panel-problem>
        <mat-icon>report_problem</mat-icon>{{ 'help.panel.problem' | translate }}
      </a>
      <a mat-button routerLink="/support" [queryParams]="{ kind: 'FEATURE', page: page }" (click)="close()" data-help-panel-feature>
        <mat-icon>lightbulb</mat-icon>{{ 'help.panel.feature' | translate }}
      </a>
      <a mat-button routerLink="/support" fragment="mine" (click)="close()" data-help-panel-requests>
        <mat-icon>forum</mat-icon>{{ 'help.panel.requests' | translate }}
      </a>
    </mat-dialog-actions>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; }
    .head { display: flex; align-items: center; justify-content: space-between; padding-right: 12px; }
    h2 { display: flex; align-items: center; gap: 8px; }
    .content { flex: 1; max-height: none; }
    .actions { flex-wrap: wrap; gap: 8px; }
    .search { width: 100%; margin-top: 4px; }
    .hits { display: grid; gap: 4px; list-style: none; margin: 0; padding: 0; }
    .hit { width: 100%; display: flex; flex-direction: column; gap: 2px; text-align: left; padding: 8px; border: 0; border-radius: 8px;
      background: none; color: inherit; font: inherit; cursor: pointer; }
    .hit:hover, .hit:focus-visible { background: var(--mat-sys-surface-container-high); }
    .hit span { color: var(--mat-sys-on-surface-variant); font-size: 0.9em; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HelpPanelComponent implements OnInit {
  /** The article shown: the page's at first, then one found by the search. */
  article = inject<HelpArticle>(MAT_DIALOG_DATA);
  /** The page the help was opened from, sent with a request to support. */
  readonly page = globalThis.location?.pathname ?? '';
  readonly query = new FormControl('', { nonNullable: true });
  /** The articles found while a search is typed (two characters or more); null otherwise, showing the article. */
  hits: HelpHit[] | null = null;
  private readonly dialogRef = inject(MatDialogRef<HelpPanelComponent>);
  private readonly searcher = inject(HelpSearchService);
  private readonly changes = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  ngOnInit(): void {
    this.query.valueChanges.pipe(debounceTime(200), map(typed => typed.trim()), distinctUntilChanged(),
      switchMap(typed => typed.length < 2 ? of(null) : this.searcher.search(typed)), takeUntilDestroyed(this.destroyRef))
      .subscribe(hits => {
        this.hits = hits;
        this.changes.markForCheck();
      });
  }

  /** Opens a found article in the panel, the search cleared. */
  show(article: HelpArticle): void {
    this.article = article;
    this.query.setValue('');
    this.hits = null;
    this.changes.markForCheck();
  }

  close(): void {
    this.dialogRef.close();
  }
}
