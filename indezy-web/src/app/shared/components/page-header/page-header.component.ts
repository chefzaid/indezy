import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * The header every page of the workspace opens with: its icon and title, an optional line under them, and its actions
 * on the right, wrapping under the title on a phone. Content marked `pageHeaderBelow` goes under the subtitle, in place
 * of it or after it. The styles are global (`.page-header` in styles.scss), so projected content takes them too.
 */
@Component({
  selector: 'app-page-header',
  imports: [MatIconModule],
  template: `
    <header class="page-header">
      <div class="page-heading">
        <h1 class="page-title">
          @if (icon) {
            <mat-icon aria-hidden="true">{{ icon }}</mat-icon>
          }
          <span>{{ title }}</span>
        </h1>
        @if (subtitle) {
          <p class="page-subtitle">{{ subtitle }}</p>
        }
        <ng-content select="[pageHeaderBelow]"></ng-content>
      </div>
      <div class="page-actions"><ng-content></ng-content></div>
    </header>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class PageHeaderComponent {
  @Input({ required: true }) title!: string;
  @Input() icon?: string;
  @Input() subtitle?: string | null;
}
