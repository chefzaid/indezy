import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { MarkdownViewComponent } from '../../shared/components/markdown/markdown-view.component';
import { HELP_ARTICLES, HelpArticle } from './help-articles';

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
 * The help of the page in use, in a panel beside it (see docs/features.md#help-center-and-legal-pages): its article,
 * with links to the full article and the whole help center.
 */
@Component({
  selector: 'app-help-panel',
  imports: [RouterLink, MatButtonModule, MatDialogModule, MatIconModule, TranslateModule, MarkdownViewComponent],
  template: `
    <div class="head">
      <h2 mat-dialog-title><mat-icon aria-hidden="true">{{ article.icon }}</mat-icon>{{ 'help.articles.' + article.slug + '.title' | translate }}</h2>
      <button mat-icon-button type="button" mat-dialog-close [aria-label]="'common.close' | translate"><mat-icon>close</mat-icon></button>
    </div>
    <mat-dialog-content class="content" data-help-panel>
      <app-markdown-view folder="help" [name]="article.slug"></app-markdown-view>
    </mat-dialog-content>
    <mat-dialog-actions class="actions">
      <a mat-stroked-button [routerLink]="['/help', article.slug]" (click)="close()" data-help-panel-full>
        <mat-icon>open_in_full</mat-icon>{{ 'help.panel.full' | translate }}
      </a>
      <a mat-button routerLink="/help" (click)="close()">{{ 'help.panel.all' | translate }}</a>
    </mat-dialog-actions>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; height: 100%; }
    .head { display: flex; align-items: center; justify-content: space-between; padding-right: 12px; }
    h2 { display: flex; align-items: center; gap: 8px; }
    .content { flex: 1; max-height: none; }
    .actions { flex-wrap: wrap; gap: 8px; }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class HelpPanelComponent {
  readonly article = inject<HelpArticle>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<HelpPanelComponent>);

  close(): void {
    this.dialogRef.close();
  }
}
