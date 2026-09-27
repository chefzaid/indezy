import { Component, Input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-project-resources',
  imports: [MatCardModule, MatIconModule, MatDividerModule, TranslateModule],
  template: `
    <mat-card>
      <mat-card-header>
        <mat-card-title><mat-icon>folder</mat-icon>{{ 'projects.detail.documentsAndLinks' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        @for (document of documents; track document) {
          <div class="document"><mat-icon>description</mat-icon><span>{{ document }}</span></div>
        }
        @if (documents.length && link) { <mat-divider></mat-divider> }
        @if (link) {
          <div class="link-row">
            <span class="label"><mat-icon>link</mat-icon>{{ 'projects.detail.projectLink' | translate }}</span>
            <a [href]="link" target="_blank" rel="noopener noreferrer">
              {{ link }}<mat-icon>open_in_new</mat-icon>
            </a>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    :host { min-width: 0; }
    mat-card-title { display: flex; align-items: center; gap: 8px; font-size: 1.1rem; }
    mat-card-title mat-icon { font-size: 1.2rem; width: 1.2rem; height: 1.2rem; color: var(--app-link); }
    mat-card-content { padding-top: 16px; }
    .document, .label, a { display: flex; align-items: center; gap: 8px; }
    .document { padding: 8px 0; overflow-wrap: anywhere; }
    .document mat-icon, .label mat-icon { flex-shrink: 0; font-size: 1rem; width: 1rem; height: 1rem; }
    .document mat-icon, .label { color: var(--app-text-muted); }
    .label { font-weight: 500; }
    .link-row { display: flex; justify-content: space-between; gap: 12px; padding: 12px 0; }
    a { color: var(--app-link); text-decoration: none; overflow-wrap: anywhere; min-width: 0; }
    a:hover { text-decoration: underline; }
    a mat-icon { flex-shrink: 0; font-size: .9rem; width: .9rem; height: .9rem; }
    @media (max-width: 600px) { .link-row { flex-direction: column; } }
  `
})
export class ProjectResourcesComponent {
  @Input() documents: string[] = [];
  @Input() link?: string;
}
