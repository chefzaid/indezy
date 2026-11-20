import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { map } from 'rxjs';

import { AuthService } from '../../services/auth/auth.service';
import { MarkdownViewComponent } from '../../shared/components/markdown/markdown-view.component';
import { PublicPageHeaderComponent } from '../../shared/components/public-page-header/public-page-header.component';

/** The legal texts, each a page open without an account. */
export const LEGAL_DOCUMENTS = ['notice', 'terms', 'privacy'] as const;
export type LegalDocument = typeof LEGAL_DOCUMENTS[number];

/** A legal text (`/legal/notice`, `/legal/terms`, `/legal/privacy`), in the interface's language, French prevailing. */
@Component({
  selector: 'app-legal-page',
  imports: [RouterLink, TranslateModule, MarkdownViewComponent, PublicPageHeaderComponent],
  template: `
    @if (!auth.isAuthenticated()) {
      <app-public-page-header></app-public-page-header>
    }
    <div class="legal-page">
      <nav class="others" [attr.aria-label]="'legal.title' | translate">
        @for (other of documents; track other) {
          <a [routerLink]="['/legal', other]" [class.active]="other === document()" [attr.aria-current]="other === document() ? 'page' : null"
            [attr.data-legal-link]="other">{{ 'legal.' + other | translate }}</a>
        }
      </nav>
      <article class="card" [attr.data-legal]="document()">
        <h1>{{ 'legal.' + document() | translate }}</h1>
        <app-markdown-view folder="legal" [name]="document()"></app-markdown-view>
      </article>
    </div>
  `,
  styles: [`
    .legal-page { display: flex; flex-direction: column; gap: 16px; width: 100%; max-width: 860px; margin: 0 auto; padding-bottom: 24px; }
    .others { display: flex; flex-wrap: wrap; gap: 8px; }
    .others a { padding: 6px 14px; border: 1px solid var(--app-border); border-radius: 999px; background: var(--app-surface);
      color: var(--app-text); font-size: 0.875rem; text-decoration: none; }
    .others a.active { border-color: transparent; background: var(--app-selected-bg); color: var(--app-link); font-weight: 600; }
    .card { padding: 24px 28px; border: 1px solid var(--app-border); border-radius: 16px; background: var(--app-surface); }
    h1 { margin: 0 0 16px; font-size: 1.625rem; }
    @media (max-width: 599.98px) { .card { padding: 18px 16px; } }
  `],
  changeDetection: ChangeDetectionStrategy.Eager
})
export class LegalPageComponent {
  readonly auth = inject(AuthService);
  readonly documents = LEGAL_DOCUMENTS;
  readonly document = toSignal(inject(ActivatedRoute).paramMap.pipe(map(params => {
    const asked = params.get('document');
    return (LEGAL_DOCUMENTS as readonly string[]).includes(asked ?? '') ? asked as LegalDocument : 'notice';
  })), { initialValue: 'notice' as LegalDocument });
}
